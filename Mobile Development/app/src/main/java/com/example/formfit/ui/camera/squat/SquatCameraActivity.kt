package com.example.formfit.ui.camera.squat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.formfit.R
import com.example.formfit.ui.camera.MainViewModel
import com.example.formfit.ui.camera.OverlayView
import com.example.formfit.ui.feedback.FeedbackSquatActivity
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SquatCameraActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "PoseLandmarkerActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var readMoreButton: Button

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private lateinit var viewModel: MainViewModel
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK

    private lateinit var overlay: OverlayView
    private lateinit var feedback_knee_position: TextView
    private lateinit var feedback_hip_position: TextView
    private lateinit var squatCountCorrectText: TextView
    private lateinit var squatCountWrongText: TextView

    private var isSquatDown = false
    private var squatCountCorrect = 0
    private var squatCountWrong = 0
    private var stage1 = false
    private var stage2 = false

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownSeconds = 10
    private lateinit var countdownTextView: TextView

    private var isAnalyzing = false

    private val predictionKneePositionList = mutableListOf<Float>()
    private val predictionHipPositionList = mutableListOf<Float>()

    private val repetitionResults = mutableListOf<FloatArray>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_squat_camera)

        previewView = findViewById(R.id.camera_preview)
        readMoreButton = findViewById(R.id.btn_read_more)
        feedback_knee_position = findViewById(R.id.feedback_knee_position)
        feedback_hip_position = findViewById(R.id.feedback_hip_position)
        squatCountCorrectText = findViewById(R.id.squat_count_correct)
        squatCountWrongText = findViewById(R.id.squat_count_wrong)
        overlay = findViewById(R.id.overlay_view)
        countdownTextView = findViewById(R.id.countdown_text)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCameraWithCountdown()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        readMoreButton.setOnClickListener {
            val intent = Intent(this, FeedbackSquatActivity::class.java)
            intent.putExtra("repetitionResults", repetitionResults.toTypedArray())
            startActivity(intent)
        }

        cameraExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = this,
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                poseLandmarkerHelperListener = this
            )
        }
    }

    private fun startCameraWithCountdown() {
        startCamera()
        countdownTextView.visibility = TextView.VISIBLE
        countdownHandler.post(object : Runnable {
            override fun run() {
                if (countdownSeconds > 0) {
                    countdownTextView.text = countdownSeconds.toString()
                    countdownSeconds--
                    countdownHandler.postDelayed(this, 1000)
                } else {
                    countdownTextView.visibility = TextView.GONE
                    isAnalyzing = true
                }

            }
        })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        preview = Preview.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(previewView.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { image ->
                    detectPose(image)
                }
            }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectPose(imageProxy: ImageProxy) {
        if (::poseLandmarkerHelper.isInitialized && isAnalyzing) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        } else {
            imageProxy.close()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        try {
            cameraExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Camera executor shutdown interrupted", e)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            if (::overlay.isInitialized) {
                overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )
                overlay.invalidate()

                // Collect predictions for the current frame

                // Check knee angle to determine squat status
                val kneeAngle = resultBundle.kneeAngle
                val hipAngle = resultBundle.hipAngle

                if (kneeAngle >= 40 && hipAngle >= 5) {
                    if (kneeAngle in 80.0..90.0 && hipAngle in 20.0..45.0) {
                        stage2 = true
                        isSquatDown = false
                        predictionKneePositionList.add(kneeAngle)
                        predictionHipPositionList.add(hipAngle)
                    } else {
                        stage1 = true
                        predictionKneePositionList.add(kneeAngle)
                        predictionHipPositionList.add(hipAngle)
                    }
                    if (kneeAngle in 80.0..90.0) {
                        feedback_knee_position.text = "Lutut: Benar"
                    } else {
                        feedback_knee_position.text = "Lutut: Salah"
                    }
                    if (hipAngle in 20.0..45.0) {
                        feedback_hip_position.text = "Pinggul: Benar"
                    } else {
                        feedback_hip_position.text = "Pinggul: Salah"
                    }
                    isSquatDown = false

                } else if (kneeAngle < 40 && hipAngle < 5) {
                    feedback_knee_position.text = "Lutut: Posisi Berdiri"
                    feedback_hip_position.text = "Pinggul: Posisi Berdiri"

                    if (stage1 && stage2) {
                        squatCountCorrect++
                    } else if (stage1 && !stage2) {
                        squatCountWrong++
                    }

                    // Reset stages after counting a squat
                    stage1 = false
                    stage2 = false
                    val avgKneeAngle = predictionKneePositionList.average().toFloat()
                    val avgHipAngle = predictionHipPositionList.average().toFloat()

                    if (!avgKneeAngle.isNaN() && !avgHipAngle.isNaN()) {
                        val resultArray = floatArrayOf(avgKneeAngle, avgHipAngle, squatCountCorrect.toFloat(), squatCountWrong.toFloat())
                        repetitionResults.add(resultArray)
                        Log.d("TEST", "Result Array: ${resultArray.contentToString()}")
                    }

                    if (!isSquatDown) {
                        predictionKneePositionList.clear()
                        predictionHipPositionList.clear()
                    }
                    isSquatDown = true
                }


                // Update the squat counts
                squatCountCorrectText.text = "Jumlah Squat Benar: $squatCountCorrect"
                squatCountWrongText.text = "Jumlah Squat Salah: $squatCountWrong"


            }
        }
    }


    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }
}
