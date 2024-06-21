package com.example.formfit.ui.camera.pushup

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
import com.example.formfit.ui.feedback.FeedbackActivity
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PushupCameraActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {

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
    private lateinit var feedback_buttom_position: TextView
    private lateinit var feedback_hand_position: TextView
    private lateinit var feedback_head_position: TextView
    private lateinit var pushUpCountCorrectTextView: TextView
    private lateinit var pushUpCountWrongTextView: TextView

    private var isPushUpDown = false
    private var pushUpCountCorrect = 0
    private var pushUpCountWrong = 0
    private var stage1 = false
    private var stage2 = false
    private var stage3 = false
    private var stage4 = false

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownSeconds = 10
    private lateinit var countdownTextView: TextView

    private var isAnalyzing = false

    private val hipPositionList = mutableListOf<Float>()
    private val handPositionList = mutableListOf<Float>()
    private val headPositionList = mutableListOf<Float>()

    private val repetitionResults = mutableListOf<FloatArray>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pushup_camera)

        previewView = findViewById(R.id.camera_preview)
        readMoreButton = findViewById(R.id.btn_read_more)
        feedback_buttom_position = findViewById(R.id.feedback_buttom_position)
        feedback_hand_position = findViewById(R.id.feedback_hand_position)
        feedback_head_position = findViewById(R.id.feedback_head_position)
        overlay = findViewById(R.id.overlay_view)
        pushUpCountCorrectTextView = findViewById(R.id.push_up_count_correct)
        pushUpCountWrongTextView = findViewById(R.id.push_up_count_wrong)
        countdownTextView = findViewById(R.id.countdown_text)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCameraWithCountdown()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        readMoreButton.setOnClickListener {
            val intent = Intent(this, FeedbackActivity::class.java)
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

        // Mengatur CameraSelector untuk kamera depan (front-facing camera)
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

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

                val hipAngle = resultBundle.hipAngle
                val handDistance = resultBundle.handDistance
                val headAngle = resultBundle.headAngle
                val elbowAngle = resultBundle.elbowAngle

                if (elbowAngle >= 100 && isPushUpDown) {
                    stage1 = true
                    hipPositionList.add(hipAngle)
                    handPositionList.add(handDistance)
                    headPositionList.add(headAngle)

                    feedback_buttom_position.text = "Pinggul: Benar"
                    feedback_hand_position.text = if (handDistance <= 1.3) "Tangan: Benar" else "Tangan: Salah"
                    feedback_head_position.text = if (headAngle >= 140) "Kepala: Benar" else "Kepala: Salah"

                    isPushUpDown = false
                } else if (elbowAngle < 100 && !isPushUpDown) {
                    stage2 = true
                    hipPositionList.add(hipAngle)
                    handPositionList.add(handDistance)
                    headPositionList.add(headAngle)

                    feedback_buttom_position.text = "Pinggul: Benar"
                    feedback_hand_position.text = if (handDistance <= 1.3) "Tangan: Benar" else "Tangan: Salah"
                    feedback_head_position.text = if (headAngle >= 140) "Kepala: Benar" else "Kepala: Salah"

                    if (stage1 && stage2) {
                        val hipPos = hipPositionList.average().toFloat()
                        val handPos = handPositionList.average().toFloat()
                        val headPos = headPositionList.average().toFloat()

                        if (hipAngle >= 160 && handDistance <= 1.3 && headAngle >= 140) {
                            pushUpCountCorrect++
                        } else {
                            pushUpCountWrong++
                        }

                        if (!hipAngle.isNaN() && !handDistance.isNaN() && !headAngle.isNaN()) {
                            val resultArray = floatArrayOf(hipAngle, handDistance, headAngle, pushUpCountCorrect.toFloat(), pushUpCountWrong.toFloat())
                            repetitionResults.add(resultArray)
                            Log.d("TEST", "Result Array: ${resultArray.contentToString()}")
                        }

                        // Reset stages after counting a push-up
                        stage1 = false
                        stage2 = false
                        hipPositionList.clear()
                        handPositionList.clear()
                        headPositionList.clear()
                    }

                    isPushUpDown = true
                }

                // Update the push-up counts
                pushUpCountCorrectTextView.text = "Push-up Benar: $pushUpCountCorrect"
                pushUpCountWrongTextView.text = "Push-up Salah: $pushUpCountWrong"
            }
        }
    }


    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }
}
