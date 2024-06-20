package com.example.formfit.ui.camera.pullup

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
import com.google.mediapipe.tasks.components.containers.Landmark
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
import kotlin.math.sqrt

class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentDelegate: Int = DELEGATE_GPU,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    val context: Context,
    val poseLandmarkerHelperListener: LandmarkerListener? = null,

    // List Input
    val input_momentum: MutableList<FloatArray> = mutableListOf<FloatArray>()
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
        val modelName = "pose_landmarker_full.task"

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

        val threshold: Float = 0.5f // Misalkan nilai threshold untuk menentukan posisi benar

        // ROM Analysis
        val labelROM = getLabelFromModel(result!!)
        val predictionROM = if (labelROM > 0.5) "Range of Motion : FULL ROM" else "Range of Motion : Kurang Naik / Kurang Turun!"

        // GRIP Analysis
        val predictionGripLabel = getGripType(result!!, thresholds = 1.5, koef = 0.5);
        val predictionGrip = when (predictionGripLabel) {
            0 -> {
                "Posisi Grip: Benar" // Jika posisi benar
            }
            1 -> {
                "Posisi Grip: Terlalu Lebar!, Kecilkan grip anda" // Jika posisi benar
            }
            2 -> {
                "Posisi Grip: Terlalu Dekat!, Lebarkan grip anda" // Jika posisi salah
            }
            else -> {
                "Posisikan kamera dengan tepat"
            }
        }

        // Momentum Used Analysis
        val momentumLabel = getMomentumLabel(result!!)
        val predictionMomentum = when {
            momentumLabel == null -> {
                "Mengayun : -" // Jika labelHeadPosition null
            }
            momentumLabel.isNaN() -> {
                "Posisikan kamera dengan tepat!" // Jika labelHeadPosition NaN
            }
            momentumLabel > threshold -> {
                "Mengayun : Bagus, tubuh lurus dan stabil!" // Jika posisi benar
            }
            else -> {
                "Mengayun : Anda mengayun, kontrol gerakan anda!"// Jika posisi salah
            }
        }

        val predictionState = getStateLabel(result!!)

        val feedbackText = "$predictionROM\n" +
                "$predictionGrip\n" +
                "$predictionMomentum\n" +
                "$predictionState\n"


        // Simpan teks ke SharedPreferences
        saveFeedback(feedbackText)
        Log.d(TAG, "$predictionROM\n")
        Log.d(TAG, "$predictionGrip\n")
        Log.d(TAG, "$predictionMomentum\n")
        Log.d(TAG, "$predictionState\n")

        poseLandmarkerHelperListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width,
                predictionROM,
                predictionGrip,
                predictionMomentum,
                predictionState
            )
        )
    }

    private fun getLabelFromModel(result: PoseLandmarkerResult): Float {
        // Obtain relevant landmarks from PoseLandmarkerResult
        val landmarks = result.worldLandmarks()
        val inputArray: FloatArray = prepareInputData(landmarks!!)

        val modelName  : String = "model_ROM.tflite"

        // Load TensorFlow Lite model
        val interpreter = Interpreter(loadModelFile(modelName))
        val outputSize = interpreter.getOutputTensor(0).shape()[1];

        // Prepare output array for TensorFlow Lite model
        val outputArray = Array(1) { FloatArray(outputSize) }

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

    private fun getMomentumLabel(result: PoseLandmarkerResult): Float? {
        // Specify model name
        val modelFileName = "model_momentum.tflite"

        // initiate intepreter momentum model
        val interpreter = Interpreter(loadModelFile(modelFileName))

        // Get ouput shape
        val outputSize = interpreter.getOutputTensor(0).shape()[1];

        // Prepare output array for TensorFlow Lite model
        val outputArray = Array(1) { FloatArray(outputSize) }

        // Extract Mediapipe Output
        val landmarks = result.worldLandmarks()
        val coords = prepareInputData(landmarks!!)

        // Append to List Input
        input_momentum.add(coords)

        if (input_momentum.size == 40) {
            Log.d(TAG, "Array Input Sudah Mencapai 40")
            val inputArray = Array(1) {input_momentum.toTypedArray()}
            interpreter.run(inputArray, outputArray)
            input_momentum.removeAt(0)

            // Ensure outputArray is populated correctly
            if (outputArray.isNotEmpty() && outputArray[0].isNotEmpty()) {
                val label = outputArray[0][0]
                Log.d(TAG, "Predicted label: $label")
                return label
            } else {
                throw RuntimeException("TensorFlow Lite output is empty or invalid.")
            }
        } else {
            return null
        }
    }

    private fun getGripType(result: PoseLandmarkerResult, thresholds: Double = 1.5, koef: Double = 0.2) : Int {
        // Final Threshold
        val FINAL_THRESHOLD = listOf(thresholds-koef, thresholds+koef)

        // Variable Grip TYpe
        val gripType : Int

        // variabel for save coordinate
        val leftWrist = mutableListOf<Float>()
        val rightWrist = mutableListOf<Float>()

        val leftShoulder = mutableListOf<Float>()
        val rightShoulder = mutableListOf<Float>()

        // get pose world landmark
        val landmarks = result.worldLandmarks()

        // coordinate index
        val wristsIndex = listOf(15, 16) // 15 for left wrist, 16 for right wrist
        val shouldersIndex = listOf(11, 12) // 11 for left wrist, 12 for right wrist


        for (landmarkList in landmarks){
            if (landmarkList != null) {
                leftWrist.add(landmarkList[wristsIndex[0]].x())
                leftWrist.add(landmarkList[wristsIndex[0]].y())
                rightWrist.add(landmarkList[wristsIndex[1]].x())
                rightWrist.add(landmarkList[wristsIndex[1]].y())

                leftShoulder.add(landmarkList[shouldersIndex[0]].x())
                leftShoulder.add(landmarkList[shouldersIndex[0]].y())
                rightShoulder.add(landmarkList[shouldersIndex[1]].x())
                rightShoulder.add(landmarkList[shouldersIndex[1]].y())
            }
        }

        val shoulderDistance = hitungJarak(leftShoulder, rightShoulder)
        val wristDistance = hitungJarak(leftWrist, rightWrist)

        val ratioDistance = wristDistance/shoulderDistance

        if (ratioDistance >= FINAL_THRESHOLD[0] && ratioDistance <= FINAL_THRESHOLD[1]) {
            gripType = 0 // Pas
        } else if (ratioDistance > FINAL_THRESHOLD[1]) {
            gripType = 1 // Lebar
        } else {
            gripType = 2 // Kecil
        }

        return gripType
    }

    private fun getStateLabel(result: PoseLandmarkerResult) : String{
        // variabel for save coordinate
        val leftWrist = mutableListOf<Float>()
        val rightWrist = mutableListOf<Float>()

        val leftShoulder = mutableListOf<Float>()
        val rightShoulder = mutableListOf<Float>()

        val leftAnkle = mutableListOf<Float>()
        val rightAnkle = mutableListOf<Float>()

        val leftElbow = mutableListOf<Float>()
        val rightElbow = mutableListOf<Float>()

        // coordinate index
        val wristsIndex = listOf(15, 16) // 15 for left wrist, 16 for right wrist
        val shouldersIndex = listOf(11, 12) // 11 for left wrist, 12 for right wrist
        val anklesIndex = listOf(27, 28) // 27 for left ankle, 28 for right ankle
        val elbowsIndex = listOf(13, 14) // 13 for left elbow, 14 for right elbow


        // Extract coordinate needed
        for (landmarkList in result.worldLandmarks()) {
            if (landmarkList != null) {
                leftWrist.add(landmarkList[wristsIndex[0]].x())
                leftWrist.add(landmarkList[wristsIndex[0]].y())
                rightWrist.add(landmarkList[wristsIndex[1]].x())
                rightWrist.add(landmarkList[wristsIndex[1]].y())

                leftShoulder.add(landmarkList[shouldersIndex[0]].x())
                leftShoulder.add(landmarkList[shouldersIndex[0]].y())
                rightShoulder.add(landmarkList[shouldersIndex[1]].x())
                rightShoulder.add(landmarkList[shouldersIndex[1]].y())

                leftAnkle.add(landmarkList[anklesIndex[0]].x())
                leftAnkle.add(landmarkList[anklesIndex[0]].y())
                rightAnkle.add(landmarkList[anklesIndex[1]].x())
                rightAnkle.add(landmarkList[anklesIndex[1]].y())

                leftElbow.add(landmarkList[elbowsIndex[0]].x())
                leftElbow.add(landmarkList[elbowsIndex[0]].y())
                rightElbow.add(landmarkList[elbowsIndex[1]].x())
                rightElbow.add(landmarkList[elbowsIndex[1]].y())
            }
        }

        // Determine to use left or right coordinate
        val dist_wrist_to_ankle_right = Math.abs(rightWrist[1] - rightAnkle[1])
        val dist_wrist_to_ankle_left = Math.abs(leftWrist[1] - leftAnkle[1])

        if (dist_wrist_to_ankle_left < dist_wrist_to_ankle_right) {
            // we use left coordinate
            return getState(leftWrist, leftElbow, leftShoulder)
        } else {
            // we use right coordinate
            return getState(rightWrist, rightElbow, rightShoulder)
        }
    }

    private fun getState(wrist: List<Float>, elbow: List<Float>, shoulder: List<Float>, angleThres : Array<Int> = arrayOf(90, 150)) : String{
        val angleElbow : Float = hitungSudut(wrist, elbow, shoulder)
        var state : String = ""
        if (angleElbow >= angleThres[1]) {
            state = "down"
        } else if (angleElbow > angleThres[0] && angleElbow < angleThres[1]) {
            state = "trans"
        } else if (angleElbow <= angleThres[1]) {
            state = "up"
        }

        return state
    }

    private fun prepareInputData(landmarks: MutableList<MutableList<Landmark>?>?): FloatArray {

        // Contoh sederhana: mengambil titik-titik landmark dan menyiapkan array float dari mereka.
        val inputArray = FloatArray(33 * 3)
        var index = 0

        if (landmarks != null) {
            for (landmarkList in landmarks) {
                if (landmarkList != null) {
                    for (landmark in landmarkList) {

                        // Menyimpan koordinat x dan y dari landmark
                        inputArray[index++] = landmark.x()
                        inputArray[index++] = landmark.y()
                        inputArray[index++] = landmark.z()
                    }
                }
            }
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
        val predictionROM: String,
        val predictionGrip: String,
        val predictionMomentum: String,
        val predictionState: String
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}