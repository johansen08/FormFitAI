package com.example.formfit.ui.camera.squat

import android.content.Context
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
import kotlin.math.round


class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentDelegate: Int = DELEGATE_GPU,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    val context: Context,
    val poseLandmarkerHelperListener: LandmarkerListener? = null
) {

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

        val kneeAngle = kneeAngle(result.landmarks()!!)
        val kneeFeedback = when {
            kneeAngle == null -> {
                "Posisikan kamera dengan tepat" // Jika labelBottomPosition null
            }
            kneeAngle.isNaN() -> {
                "Posisikan kamera dengan tepat" // Jika labelBottomPosition NaN
            }
            kneeAngle >= 0 && kneeAngle < 80 -> {
                "Lutut: Posisi Berdiri" // Jika posisi benar
            }
            kneeAngle >= 80 && kneeAngle <= 90 -> {
                "Lutut: Benar" // Jika posisi benar
            }
            else -> {
                "Lutut: sudut ${round(kneeAngle)}, squat terlalu dalam" // Jika posisi salah
            }
        }

        val hipAngle = hipAngle(result.landmarks()!!)
        val hipFeedback = when {
            hipAngle == null -> {
                "Posisikan kamera dengan tepat" // Jika labelBottomPosition null
            }
            hipAngle.isNaN() -> {
                "Posisikan kamera dengan tepat" // Jika labelBottomPosition NaN
            }
            hipAngle >= 0 && hipAngle < 5 -> {
                "Lutut: Posisi Berdiri" // Jika posisi benar
            }
            hipAngle >= 20 && hipAngle <= 45 -> {
                "Lutut: Benar" // Jika posisi benar
            }
            else -> {
                "Lutut: sudut ${round(hipAngle)}, posisi punggung tidak tepat" // Jika posisi salah
            }
        }

        poseLandmarkerHelperListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width,
                kneeAngle,
                kneeFeedback,
                hipAngle,
                hipFeedback
            )
        )
    }

    private fun kneeAngle(landmarks: List<List<NormalizedLandmark>?>?): Float {
        // Definisikan titik-titik yang akan digunakan untuk menghitung sudut
        val hipLeft = mutableListOf<Float>()
        val kneeLeft = mutableListOf<Float>()
        val vertKneeLeft = mutableListOf<Float>()
        val hipRight = mutableListOf<Float>()
        val kneeRight = mutableListOf<Float>()
        val vertKneeRight = mutableListOf<Float>()

        var kneeAngle = Float.NaN

        // Ekstraksi landmark dari hasil deteksi, jika landmarks tidak null
        landmarks?.let { landmarkList ->
            for (landmarks in landmarkList) {
                landmarks?.let {
                    for (i in landmarks.indices) {
                        val landmark = landmarks.getOrNull(i)

                        // Simpan koordinat titik-titik yang diperlukan untuk menghitung sudut
                        when (i) {
                            23 -> { // LEFT_HIP
                                landmark?.let {
                                    hipLeft.add(it.x())
                                    hipLeft.add(it.y())
                                }
                            }
                            25 -> { // LEFT_KNEE
                                landmark?.let {
                                    kneeLeft.add(it.x())
                                    kneeLeft.add(it.y())
                                    vertKneeLeft.add(it.x())
                                    vertKneeLeft.add(0f) // Titik bantu vertikal dari knee
                                }
                            }
                            24 -> { // RIGHT_HIP
                                landmark?.let {
                                    hipRight.add(it.x())
                                    hipRight.add(it.y())
                                }
                            }
                            26 -> { // RIGHT_KNEE
                                landmark?.let {
                                    kneeRight.add(it.x())
                                    kneeRight.add(it.y())
                                    vertKneeRight.add(it.x())
                                    vertKneeRight.add(0f) // Titik bantu vertikal dari knee
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hitung sudut kiri dan sudut kanan menggunakan fungsi hitungSudut yang ada
        val sudutKiri = if (hipLeft.isNotEmpty() && kneeLeft.isNotEmpty() && vertKneeLeft.isNotEmpty()) {
            hitungSudut(hipLeft, kneeLeft, vertKneeLeft)
        } else {
            // Nilai default jika data tidak lengkap
            Float.NaN
        }

        val sudutKanan = if (hipRight.isNotEmpty() && kneeRight.isNotEmpty() && vertKneeRight.isNotEmpty()) {
            hitungSudut(hipRight, kneeRight, vertKneeRight)
        } else {
            // Nilai default jika data tidak lengkap
            Float.NaN
        }

        // Menyimpan sudut kiri dan kanan di array input, jika data lengkap
        if (!sudutKiri.isNaN() && !sudutKanan.isNaN()) {
            kneeAngle = (sudutKanan + sudutKiri) / 2
        }

        return kneeAngle
    }

    private fun hipAngle(landmarks: List<List<NormalizedLandmark>?>?): Float {
        // Definisikan titik-titik yang akan digunakan untuk menghitung sudut
        val hipLeft = mutableListOf<Float>()
        val shoulderLeft = mutableListOf<Float>()
        val vertHipLeft = mutableListOf<Float>()
        val hipRight = mutableListOf<Float>()
        val shoulderRight = mutableListOf<Float>()
        val vertHipRight = mutableListOf<Float>()

        var hipAngle = Float.NaN

        // Ekstraksi landmark dari hasil deteksi, jika landmarks tidak null
        landmarks?.let { landmarkList ->
            for (landmarks in landmarkList) {
                landmarks?.let {
                    for (i in landmarks.indices) {
                        val landmark = landmarks.getOrNull(i)

                        // Simpan koordinat titik-titik yang diperlukan untuk menghitung sudut
                        when (i) {
                            23 -> { // LEFT_HIP
                                landmark?.let {
                                    hipLeft.add(it.x())
                                    hipLeft.add(it.y())
                                }
                            }
                            11 -> { // LEFT_SHOULDER
                                landmark?.let {
                                    shoulderLeft.add(it.x())
                                    shoulderLeft.add(it.y())
                                }
                            }
                            24 -> { // RIGHT_HIP
                                landmark?.let {
                                    hipRight.add(it.x())
                                    hipRight.add(it.y())
                                }
                            }
                            12 -> { // RIGHT_SHOULDER
                                landmark?.let {
                                    shoulderRight.add(it.x())
                                    shoulderRight.add(it.y())
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tambahkan koordinat vertikal dari hip
        if (hipLeft.isNotEmpty()) {
            vertHipLeft.add(hipLeft[0])
            vertHipLeft.add(0f) // Titik bantu vertikal dari hip
        }

        if (hipRight.isNotEmpty()) {
            vertHipRight.add(hipRight[0])
            vertHipRight.add(0f) // Titik bantu vertikal dari hip
        }

        // Hitung sudut kiri dan sudut kanan menggunakan fungsi hitungSudut yang ada
        val sudutKiri = if (shoulderLeft.isNotEmpty() && hipLeft.isNotEmpty() && vertHipLeft.isNotEmpty()) {
            hitungSudut(shoulderLeft, hipLeft, vertHipLeft)
        } else {
            // Nilai default jika data tidak lengkap
            Float.NaN
        }

        val sudutKanan = if (shoulderRight.isNotEmpty() && hipRight.isNotEmpty() && vertHipRight.isNotEmpty()) {
            hitungSudut(shoulderRight, hipRight, vertHipRight)
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
        val kneeAngle: Float,
        val kneeFeedback: String,
        val hipAngle: Float,
        val hipFeedback: String
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}