package com.example.formfit.ui.camera.pushup

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentDelegate: Int = DELEGATE_GPU,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    val context: Context,
    val poseLandmarkerHelperListener: LandmarkerListener? = null
) {
    private val sharedPref: SharedPreferences by lazy {
        context.getSharedPreferences(
            "feedback_prefs",
            Context.MODE_PRIVATE
        )
    }

    fun saveFeedback(text: String) {
        sharedPref.edit().apply {
            putString("feedback_text", text)
            apply()
        }
    }
    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    fun isClose(): Boolean {
        return poseLandmarker == null
    }

    fun setupPoseLandmarker() {
        val baseOptionBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }
        // mediapipe pose estimation detection model
        val modelName = "pose_landmarker_lite.task"

        baseOptionBuilder.setModelAssetPath(modelName)
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (poseLandmarkerHelperListener == null) {
                    throw IllegalStateException(
                        "poseLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }
            else -> {
                // no-op
            }
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            val optionsBuilder =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minPoseTrackingConfidence)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setRunningMode(runningMode)
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            poseLandmarker =
                PoseLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize. See error logs for " +
                        "details", GPU_ERROR
            )
            Log.e(
                TAG,
                "Image classifier failed to load model with error: " + e.message
            )
        }
    }

    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream" +
                        " while not using RunningMode.LIVE_STREAM"
            )
        }
        Log.d(TAG, "detectLiveStream called with imageProxy: $imageProxy")
        val frameTime = SystemClock.uptimeMillis()

        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )

        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        // Up and Down Analysis
        val labelUpDown = getLabelFromModel(result, "model_up_down.tflite")
        val predictionUpDown = if (labelUpDown > 0.5) "Up" else "Down"
        // Bottom Position Analysis
        val labelBottomPosition = getLabelFromModel(result, "model_bottom_position.tflite")
        val threshold = 0.5f // Misalkan nilai threshold untuk menentukan posisi benar

        val predictionBottomPosition = when {
            labelBottomPosition == null -> {
                "Posisikan kamera dengan tepat" // Jika labelBottomPosition null
            }
            labelBottomPosition.isNaN() -> {
                "Posisikan kamera dengan tepat" // Jika labelBottomPosition NaN
            }
            labelBottomPosition > threshold -> {
                "Posisi Pinggul: Benar" // Jika posisi benar
            }
            else -> {
                "Posisi Pinggul: Salah, Lusurkan pinggang Anda" // Jika posisi salah
            }
        }
        // Hand Position Analysis
        val labelHandPosition = getLabelFromModel(result, "model_elbow_position.tflite")
        val predictionHandPosition = when {
            labelHandPosition == null -> {
                "Posisikan tangan dengan tepat" // Jika labelHandPosition null
            }
            labelHandPosition.isNaN() -> {
                "Posisikan tangan dengan tepat" // Jika labelHandPosition NaN
            }
            labelHandPosition > threshold -> {
                "Posisi Tangan: Benar" // Jika posisi benar
            }
            else -> {
                "Posisi Tangan: Salah, Posisikan siku sejajar tubuh"// Jika posisi salah
            }
        }

// Head Position Analysis
        val labelHeadPosition = getLabelFromModel(result, "model_head_position.tflite")
        val predictionHeadPosition = when {
            labelHeadPosition == null -> {
                "Posisikan kepala dengan tepat" // Jika labelHeadPosition null
            }
            labelHeadPosition.isNaN() -> {
                "Posisikan kepala dengan tepat" // Jika labelHeadPosition NaN
            }
            labelHeadPosition > threshold -> {
                "Posisi Kepala: Benar" // Jika posisi benar
            }
            else -> {
                "Posisi Kepala: Salah, Jangan tekuk kepala Anda"// Jika posisi salah
            }
        }
        val feedbackText = "$predictionUpDown\n" +
                "$predictionBottomPosition\n" +
                "$predictionHandPosition\n" +
                "$predictionHeadPosition\n"

        // Simpan teks ke SharedPreferences
        saveFeedback(feedbackText)
        Log.d(TAG, "$predictionUpDown\n")
        Log.d(TAG, "$predictionBottomPosition\n")
        Log.d(TAG, "$predictionHandPosition\n")
        Log.d(TAG, "$predictionHeadPosition\n")
        // Analysis without ML model
        // Bottom Position Analysis
        val hipAngle = dataHipAngle(result.landmarks()!!)
        val hipFeedback = when {
            hipAngle == null -> {
                "Posisikan kamera dengan tepat" // Jika labelBottomPosition null
            }
            hipAngle.isNaN() -> {
                "Posisikan kamera dengan tepat" // Jika labelBottomPosition NaN
            }
            hipAngle >= 170 -> {
                "Pinggul: Benar" // Jika posisi benar
            }
            else -> {
                "Pinggul: sudut ${round(hipAngle)}, pinggul tidak lurus" // Jika posisi salah
            }
        }

        val handDistance = handDistance(result.landmarks()!!)
        val handFeedback = when {
            handDistance == null -> {
                "Posisikan kamera dengan tepat" // Jika labelBottomPosition null
            }
            handDistance.isNaN() -> {
                "Posisikan kamera dengan tepat" // Jika labelBottomPosition NaN
            }
            handDistance < 1.3 -> {
                "Tangan: Benar, jarak %.2f".format(handDistance) // Jika posisi benar
            }
            else -> {
                "Tangan: ratio jarak tangan dan siku $%.2f, siku tidak sejajar tubuh".format(handDistance) // Jika posisi salah
            }
        }

        val headAngle = headAngle(result.landmarks()!!)
        val headFeedback = when {
            headAngle == null -> {
                "Posisikan kamera dengan tepat" // If headAngle is null
            }
            headAngle.isNaN() -> {
                "Posisikan kamera dengan tepat" // If headAngle is NaN
            }
            headAngle >= 140 -> {
                "Kepala: Benar" // If headAngle is greater than 170
            }
            else -> {
                "Kepala: sudut ${round(headAngle)}, kepala tidak lurus" // For all other cases
            }
        }

        val elbowAngle = elbowAngle(result.landmarks()!!)

        poseLandmarkerHelperListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width,
                predictionUpDown,
                predictionBottomPosition,
                predictionHandPosition,
                predictionHeadPosition,
                labelUpDown,
                labelBottomPosition,
                labelHandPosition,
                labelHeadPosition,
                hipAngle,
                hipFeedback,
                headAngle,
                headFeedback,
                handDistance,
                handFeedback,
                elbowAngle
            )
        )
    }

    private fun elbowAngle(landmarks: List<List<NormalizedLandmark>?>?): Float {
        val indices = listOf(11, 12, 13, 14, 15, 16)
        var elbowAngle = Float.NaN

        val titik11 = mutableListOf<Float>()
        val titik12 = mutableListOf<Float>()
        val titik13 = mutableListOf<Float>()
        val titik14 = mutableListOf<Float>()
        val titik15 = mutableListOf<Float>()
        val titik16 = mutableListOf<Float>()

        landmarks?.let { landmarkList ->
            for (landmarkList in landmarkList) {
                landmarkList?.let {
                    for (i in indices) {
                        val landmark = it.getOrNull(i)

                        when (i) {
                            11 -> {
                                landmark?.let {
                                    titik11.add(it.x())
                                    titik11.add(it.y())
                                }
                            }
                            12 -> {
                                landmark?.let {
                                    titik12.add(it.x())
                                    titik12.add(it.y())
                                }
                            }
                            13 -> {
                                landmark?.let {
                                    titik13.add(it.x())
                                    titik13.add(it.y())
                                }
                            }
                            14 -> {
                                landmark?.let {
                                    titik14.add(it.x())
                                    titik14.add(it.y())
                                }
                            }
                            15 -> {
                                landmark?.let {
                                    titik15.add(it.x())
                                    titik15.add(it.y())
                                }
                            }
                            16 -> {
                                landmark?.let {
                                    titik16.add(it.x())
                                    titik16.add(it.y())
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hitung sudut kiri dan sudut kanan menggunakan fungsi hitungSudut yang ada
        val sudutKiri = if (titik11.isNotEmpty() && titik13.isNotEmpty() && titik15.isNotEmpty()) {
            hitungSudut(titik11, titik13, titik15)
        } else {
            // Nilai default jika data tidak lengkap
            Float.NaN
        }

        val sudutKanan = if (titik12.isNotEmpty() && titik14.isNotEmpty() && titik16.isNotEmpty()) {
            hitungSudut(titik12, titik14, titik16)
        } else {
            // Nilai default jika data tidak lengkap
            Float.NaN
        }

        // Menyimpan sudut kiri dan kanan di array input, jika data lengkap

        if (!sudutKiri.isNaN() && !sudutKanan.isNaN()) {
            elbowAngle = (sudutKanan + sudutKiri) / 2
        }


        return elbowAngle
    }

    private fun getLabelFromModel(result: PoseLandmarkerResult, modelFileName: String): Float {
        // Obtain relevant landmarks from PoseLandmarkerResult
        val landmarks = result.landmarks()
        val inputArray: FloatArray = when (modelFileName) {
            "model_up_down.tflite" -> prepareInputData(landmarks!!)
            "model_bottom_position.tflite" -> prepareInputData2(landmarks!!)
            "model_elbow_position.tflite" -> prepareInputData3(landmarks!!)
            "model_head_position.tflite" -> prepareInputData4(landmarks!!)
            else -> throw IllegalArgumentException("Unsupported model file name: $modelFileName")
        }

        // Load TensorFlow Lite model
        val interpreter = Interpreter(loadModelFile(modelFileName))

        // Prepare output array for TensorFlow Lite model
        val outputArray = Array(1) { FloatArray(1) }

        // Run TensorFlow Lite model inference
        interpreter.run(inputArray, outputArray)

        // Ensure outputArray is populated correctly
        if (outputArray.isNotEmpty() && outputArray[0].isNotEmpty()) {
            val label = outputArray[0][0]
            Log.d(TAG, "Predicted label: $label")
            return label
        } else {
            throw RuntimeException("TensorFlow Lite output is empty or invalid.")
        }
    }


    private fun prepareInputData(landmarks: List<List<NormalizedLandmark>?>): FloatArray {
        // Memilih landmark dari indeks 11 hingga 33 (inklusif)
        val startIndex = 10
        val endIndex = 32

        // Menghitung jumlah landmark yang akan diproses
        val numLandmarks = endIndex - startIndex + 1

        // Contoh sederhana: mengambil titik-titik landmark dan menyiapkan array float dari mereka.
        val inputArray = FloatArray(numLandmarks * 3)
        var index = 0

        for (landmarkList in landmarks) {
            if (landmarkList != null) {
                for (i in startIndex..endIndex) {
                    val landmark = landmarkList[i]

                    // Menyimpan koordinat x dan y dari landmark
                    inputArray[index++] = landmark.x()
                    inputArray[index++] = landmark.y()
                    inputArray[index++] = landmark.z()
                }
            }
        }

        return inputArray
    }

    private fun handDistance(landmarks: List<List<NormalizedLandmark>?>?): Float {
        // Memilih landmark dari indeks 13 hingga 16 (inklusif)
        val startIndex = 13
        val endIndex = 16

        // Array untuk menyimpan jarak dan rasio elbow to wrist
        var handDistance = Float.NaN  // Inisialisasi dengan NaN (Not a Number)

        // Definisikan titik-titik yang akan digunakan untuk menghitung jarak
        val titik13 = mutableListOf<Float>()
        val titik14 = mutableListOf<Float>()
        val titik15 = mutableListOf<Float>()
        val titik16 = mutableListOf<Float>()

        // Ekstraksi landmark dari hasil deteksi, jika landmarks tidak null
        landmarks?.let { landmarkList ->
            for (landmarkList in landmarkList) {
                landmarkList?.let {
                    for (i in startIndex..endIndex) {
                        val landmark = it.getOrNull(i)

                        // Simpan koordinat titik-titik yang diperlukan untuk menghitung jarak
                        when (i) {
                            13 -> {
                                landmark?.let {
                                    titik13.add(it.y())
                                    titik13.add(it.z())
                                }
                            }
                            14 -> {
                                landmark?.let {
                                    titik14.add(it.y())
                                    titik14.add(it.z())
                                }
                            }
                            15 -> {
                                landmark?.let {
                                    titik15.add(it.y())
                                    titik15.add(it.z())
                                }
                            }
                            16 -> {
                                landmark?.let {
                                    titik16.add(it.y())
                                    titik16.add(it.z())
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hitung jarak antara titik-titik yang relevan
        val jarakWrist = if (titik15.isNotEmpty() && titik16.isNotEmpty()) {
            hitungJarak(titik15, titik16)
        } else {
            Float.NaN
        }

        val jarakElbow = if (titik13.isNotEmpty() && titik14.isNotEmpty()) {
            hitungJarak(titik13, titik14)
        } else {
            Float.NaN
        }

        // Hitung rasio elbow to wrist
        val elbowToWristRatio = jarakElbow / jarakWrist

        // Menyimpan hasil jarak dan rasio di dalam array input
        if (!jarakWrist.isNaN() && !jarakElbow.isNaN() && !elbowToWristRatio.isNaN()) {
            handDistance = elbowToWristRatio.toFloat()
        }

        return handDistance
    }

    private fun dataHipAngle(landmarks: List<List<NormalizedLandmark>?>?): Float {
        // Memilih landmark dari indeks 11 hingga 33 (inklusif)
        val startIndex = 11
        val endIndex = 33

        // Definisikan titik-titik yang akan digunakan untuk menghitung sudut
        val titik11 = mutableListOf<Float>()
        val titik12 = mutableListOf<Float>()
        val titik23 = mutableListOf<Float>()
        val titik24 = mutableListOf<Float>()
        val titik25 = mutableListOf<Float>()
        val titik26 = mutableListOf<Float>()

        var hipAngle = Float.NaN

        // Ekstraksi landmark dari hasil deteksi, jika landmarks tidak null
        landmarks?.let { landmarkList ->
            for (landmarkList in landmarkList) {
                landmarkList?.let {
                    for (i in startIndex..endIndex) {
                        val landmark = it.getOrNull(i)

                        // Simpan koordinat titik-titik yang diperlukan untuk menghitung sudut
                        when (i) {
                            11 -> {
                                landmark?.let {
                                    titik11.add(it.x())
                                    titik11.add(it.y())
                                }
                            }
                            12 -> {
                                landmark?.let {
                                    titik12.add(it.x())
                                    titik12.add(it.y())
                                }
                            }
                            23 -> {
                                landmark?.let {
                                    titik23.add(it.x())
                                    titik23.add(it.y())
                                }
                            }
                            24 -> {
                                landmark?.let {
                                    titik24.add(it.x())
                                    titik24.add(it.y())
                                }
                            }
                            25 -> {
                                landmark?.let {
                                    titik25.add(it.x())
                                    titik25.add(it.y())
                                }
                            }
                            26 -> {
                                landmark?.let {
                                    titik26.add(it.x())
                                    titik26.add(it.y())
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hitung sudut kiri dan sudut kanan menggunakan fungsi hitungSudut yang ada
        val sudutKiri = if (titik11.isNotEmpty() && titik23.isNotEmpty() && titik25.isNotEmpty()) {
            hitungSudut(titik11, titik23, titik25)
        } else {
            // Nilai default jika data tidak lengkap
            Float.NaN
        }

        val sudutKanan = if (titik12.isNotEmpty() && titik24.isNotEmpty() && titik26.isNotEmpty()) {
            hitungSudut(titik12, titik24, titik26)
        } else {
            // Nilai default jika data tidak lengkap
            Float.NaN
        }

        // Menyimpan sudut kiri dan kanan di array input, jika data lengkap

        if (!sudutKiri.isNaN() && !sudutKanan.isNaN()) {
            hipAngle = (sudutKanan + sudutKiri) / 2
        }

        return hipAngle
    }

    private fun headAngle(landmarks: List<List<NormalizedLandmark>?>?): Float {
        val indices = listOf(7, 8, 11, 12, 23, 24)
        var headAngle = Float.NaN

        val titik7 = mutableListOf<Float>()
        val titik8 = mutableListOf<Float>()
        val titik11 = mutableListOf<Float>()
        val titik12 = mutableListOf<Float>()
        val titik23 = mutableListOf<Float>()
        val titik24 = mutableListOf<Float>()

        landmarks?.let { landmarkList ->
            for (landmarkList in landmarkList) {
                landmarkList?.let {
                    for (i in indices) {
                        val landmark = it.getOrNull(i)

                        when (i) {
                            7 -> {
                                landmark?.let {
                                    titik7.add(it.x())
                                    titik7.add(it.y())
                                }
                            }
                            8 -> {
                                landmark?.let {
                                    titik8.add(it.x())
                                    titik8.add(it.y())
                                }
                            }
                            11 -> {
                                landmark?.let {
                                    titik11.add(it.x())
                                    titik11.add(it.y())
                                }
                            }
                            12 -> {
                                landmark?.let {
                                    titik12.add(it.x())
                                    titik12.add(it.y())
                                }
                            }
                            23 -> {
                                landmark?.let {
                                    titik23.add(it.x())
                                    titik23.add(it.y())
                                }
                            }
                            24 -> {
                                landmark?.let {
                                    titik24.add(it.x())
                                    titik24.add(it.y())
                                }
                            }
                        }
                    }
                }
            }
        }

        val tengahTelinga = if (titik7.isNotEmpty() && titik8.isNotEmpty()) {
            hitungTitikTengah(titik7, titik8)
        } else {
            listOf(Float.NaN, Float.NaN)
        }

        val tengahBahu = if (titik11.isNotEmpty() && titik12.isNotEmpty()) {
            hitungTitikTengah(titik11, titik12)
        } else {
            listOf(Float.NaN, Float.NaN)
        }

        val tengahPinggul = if (titik23.isNotEmpty() && titik24.isNotEmpty()) {
            hitungTitikTengah(titik23, titik24)
        } else {
            listOf(Float.NaN, Float.NaN)
        }

        val sudut = if (!tengahTelinga.contains(Float.NaN) && !tengahBahu.contains(Float.NaN) &&
            !tengahPinggul.contains(Float.NaN)) {
            hitungSudut(tengahTelinga, tengahBahu, tengahPinggul)
        } else {
            Float.NaN
        }

        // Menyimpan sudut di dalam array input, jika data lengkap
        if (!sudut.isNaN()) {
            headAngle = sudut
        }

        return headAngle
    }


    private fun prepareInputData2(landmarks: List<List<NormalizedLandmark>?>?): FloatArray {
        // Memilih landmark dari indeks 11 hingga 33 (inklusif)
        val startIndex = 11
        val endIndex = 33

        // Array untuk menyimpan sudut kiri dan kanan
        val inputArray = FloatArray(2) { Float.NaN } // Inisialisasi dengan NaN (Not a Number)

        // Definisikan titik-titik yang akan digunakan untuk menghitung sudut
        val titik11 = mutableListOf<Float>()
        val titik12 = mutableListOf<Float>()
        val titik23 = mutableListOf<Float>()
        val titik24 = mutableListOf<Float>()
        val titik25 = mutableListOf<Float>()
        val titik26 = mutableListOf<Float>()

        // Ekstraksi landmark dari hasil deteksi, jika landmarks tidak null
        landmarks?.let { landmarkList ->
            for (landmarkList in landmarkList) {
                landmarkList?.let {
                    for (i in startIndex..endIndex) {
                        val landmark = it.getOrNull(i)

                        // Simpan koordinat titik-titik yang diperlukan untuk menghitung sudut
                        when (i) {
                            11 -> {
                                landmark?.let {
                                    titik11.add(it.x())
                                    titik11.add(it.y())
                                }
                            }
                            12 -> {
                                landmark?.let {
                                    titik12.add(it.x())
                                    titik12.add(it.y())
                                }
                            }
                            23 -> {
                                landmark?.let {
                                    titik23.add(it.x())
                                    titik23.add(it.y())
                                }
                            }
                            24 -> {
                                landmark?.let {
                                    titik24.add(it.x())
                                    titik24.add(it.y())
                                }
                            }
                            25 -> {
                                landmark?.let {
                                    titik25.add(it.x())
                                    titik25.add(it.y())
                                }
                            }
                            26 -> {
                                landmark?.let {
                                    titik26.add(it.x())
                                    titik26.add(it.y())
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hitung sudut kiri dan sudut kanan menggunakan fungsi hitungSudut yang ada
        val sudutKiri = if (titik11.isNotEmpty() && titik23.isNotEmpty() && titik25.isNotEmpty()) {
            hitungSudut(titik11, titik23, titik25)
        } else {
            // Nilai default jika data tidak lengkap
            Float.NaN
        }

        val sudutKanan = if (titik12.isNotEmpty() && titik24.isNotEmpty() && titik26.isNotEmpty()) {
            hitungSudut(titik12, titik24, titik26)
        } else {
            // Nilai default jika data tidak lengkap
            Float.NaN
        }

        // Menyimpan sudut kiri dan kanan di array input, jika data lengkap
        if (!sudutKiri.isNaN() && !sudutKanan.isNaN()) {
            inputArray[0] = sudutKiri
            inputArray[1] = sudutKanan
        }

        return inputArray
    }

    private fun prepareInputData3(landmarks: List<List<NormalizedLandmark>?>?): FloatArray {
        // Memilih landmark dari indeks 13 hingga 16 (inklusif)
        val startIndex = 13
        val endIndex = 16

        // Array untuk menyimpan jarak dan rasio elbow to wrist
        val inputArray = FloatArray(3) { Float.NaN } // Inisialisasi dengan NaN (Not a Number)

        // Definisikan titik-titik yang akan digunakan untuk menghitung jarak
        val titik13 = mutableListOf<Float>()
        val titik14 = mutableListOf<Float>()
        val titik15 = mutableListOf<Float>()
        val titik16 = mutableListOf<Float>()

        // Ekstraksi landmark dari hasil deteksi, jika landmarks tidak null
        landmarks?.let { landmarkList ->
            for (landmarkList in landmarkList) {
                landmarkList?.let {
                    for (i in startIndex..endIndex) {
                        val landmark = it.getOrNull(i)

                        // Simpan koordinat titik-titik yang diperlukan untuk menghitung jarak
                        when (i) {
                            13 -> {
                                landmark?.let {
                                    titik13.add(it.x())
                                    titik13.add(it.y())
                                }
                            }
                            14 -> {
                                landmark?.let {
                                    titik14.add(it.x())
                                    titik14.add(it.y())
                                }
                            }
                            15 -> {
                                landmark?.let {
                                    titik15.add(it.x())
                                    titik15.add(it.y())
                                }
                            }
                            16 -> {
                                landmark?.let {
                                    titik16.add(it.x())
                                    titik16.add(it.y())
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hitung jarak antara titik-titik yang relevan
        val jarakWrist = if (titik15.isNotEmpty() && titik16.isNotEmpty()) {
            hitungJarak(titik15, titik16)
        } else {
            Float.NaN
        }

        val jarakElbow = if (titik13.isNotEmpty() && titik14.isNotEmpty()) {
            hitungJarak(titik13, titik14)
        } else {
            Float.NaN
        }

        // Hitung rasio elbow to wrist
        val elbowToWristRatio = jarakElbow / jarakWrist

        // Menyimpan hasil jarak dan rasio di dalam array input
        if (!jarakWrist.isNaN() && !jarakElbow.isNaN() && !elbowToWristRatio.isNaN()) {
            inputArray[0] = jarakWrist
            inputArray[1] = jarakElbow
            inputArray[2] = elbowToWristRatio.toFloat()
        }

        return inputArray
    }

    private fun prepareInputData4(landmarks: List<List<NormalizedLandmark>?>?): FloatArray {
        // Memilih landmark dari indeks 7, 8, 11, 12, 23, dan 24
        val indices = listOf(7, 8, 11, 12, 23, 24)

        // Array untuk menyimpan sudut antara titik-titik
        val inputArray = FloatArray(1) { Float.NaN } // Inisialisasi dengan NaN (Not a Number)

        // Definisikan titik-titik yang akan digunakan untuk menghitung sudut
        val titik7 = mutableListOf<Float>()
        val titik8 = mutableListOf<Float>()
        val titik11 = mutableListOf<Float>()
        val titik12 = mutableListOf<Float>()
        val titik23 = mutableListOf<Float>()
        val titik24 = mutableListOf<Float>()

        // Ekstraksi landmark dari hasil deteksi, jika landmarks tidak null
        landmarks?.let { landmarkList ->
            for (landmarkList in landmarkList) {
                landmarkList?.let {
                    for (i in indices) {
                        val landmark = it.getOrNull(i)

                        // Simpan koordinat titik-titik yang diperlukan untuk menghitung sudut
                        when (i) {
                            7 -> {
                                landmark?.let {
                                    titik7.add(it.x())
                                    titik7.add(it.y())
                                }
                            }
                            8 -> {
                                landmark?.let {
                                    titik8.add(it.x())
                                    titik8.add(it.y())
                                }
                            }
                            11 -> {
                                landmark?.let {
                                    titik11.add(it.x())
                                    titik11.add(it.y())
                                }
                            }
                            12 -> {
                                landmark?.let {
                                    titik12.add(it.x())
                                    titik12.add(it.y())
                                }
                            }
                            23 -> {
                                landmark?.let {
                                    titik23.add(it.x())
                                    titik23.add(it.y())
                                }
                            }
                            24 -> {
                                landmark?.let {
                                    titik24.add(it.x())
                                    titik24.add(it.y())
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hitung titik tengah dari setiap bagian tubuh
        val tengahTelinga = if (titik7.isNotEmpty() && titik8.isNotEmpty()) {
            hitungTitikTengah(titik7, titik8)
        } else {
            listOf(Float.NaN, Float.NaN)
        }

        val tengahBahu = if (titik11.isNotEmpty() && titik12.isNotEmpty()) {
            hitungTitikTengah(titik11, titik12)
        } else {
            listOf(Float.NaN, Float.NaN)
        }

        val tengahPinggul = if (titik23.isNotEmpty() && titik24.isNotEmpty()) {
            hitungTitikTengah(titik23, titik24)
        } else {
            listOf(Float.NaN, Float.NaN)
        }

        // Hitung sudut antara titik tengah
        val sudut = if (!tengahTelinga.contains(Float.NaN) && !tengahBahu.contains(Float.NaN) &&
            !tengahPinggul.contains(Float.NaN)) {
            hitungSudut(tengahTelinga, tengahBahu, tengahPinggul)
        } else {
            // Nilai default jika data tidak lengkap
            Float.NaN
        }

        // Menyimpan sudut di dalam array input, jika data lengkap
        if (!sudut.isNaN()) {
            inputArray[0] = sudut
        }

        return inputArray
    }

    private fun hitungTitikTengah(titik1: List<Float>, titik2: List<Float>): List<Float> {
        val tengahX = (titik1[0] + titik2[0]) / 2
        val tengahY = (titik1[1] + titik2[1]) / 2
        return listOf(tengahX, tengahY)
    }


    fun hitungJarak(a: List<Float>, b: List<Float>): Float {
        val squaredSum = (a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2)
        return sqrt(squaredSum)
    }


    // Fungsi untuk menghitung sudut antara tiga titik
    private fun hitungSudut(a: List<Float>, b: List<Float>, c: List<Float>): Float {
        val aArray = a.toFloatArray()
        val bArray = b.toFloatArray()
        val cArray = c.toFloatArray()

        val radians = Math.atan2((cArray[1] - bArray[1]).toDouble(), (cArray[0] - bArray[0]).toDouble()) -
                Math.atan2((aArray[1] - bArray[1]).toDouble(), (aArray[0] - bArray[0]).toDouble())
        var sudut = Math.abs(radians * 180.0 / Math.PI)

        if (sudut > 180.0) {
            sudut = 360 - sudut
        }

        return sudut.toFloat()
    }



    private fun loadModelFile(modelFileName: String): MappedByteBuffer {
        val modelFileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(modelFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = modelFileDescriptor.startOffset
        val declaredLength = modelFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun returnLivestreamError(error: RuntimeException) {
        poseLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    companion object {
        const val TAG = "PoseLandmarkerHelper"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_POSES = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val predictionUpDown: String,
        val predictionBottomPosition: String,
        val predictionHandPosition: String,
        val predictionHeadPosition: String,
        val labelUpDown: Float,
        val labelBottomPosition: Float,
        val labelHandPosition: Float,
        val labelHeadPosition: Float,
        val hipAngle: Float,
        val hipFeedback: String,
        val headAngle: Float,
        val headFeedback: String,
        val handDistance: Float,
        val handFeedback: String,
        val elbowAngle: Float


    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}