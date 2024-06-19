package com.example.formfit.ui.camera.pullup

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
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.formfit.R
import com.example.formfit.ui.camera.MainViewModel
import com.example.formfit.ui.camera.OverlayView
import com.example.formfit.ui.camera.pullup.PoseLandmarkerHelper
import com.example.formfit.ui.feedback.FeedbackActivity
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PullupCameraActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {

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
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    private lateinit var overlay: OverlayView
    private lateinit var feedback_grip: TextView
    private lateinit var feedback_momentum: TextView
    private lateinit var feedback_ROM: TextView
    private lateinit var feedback_counter: TextView

    private var isPushUpDown = false
    private var pushUpCount = 0

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownSeconds = 10
    private lateinit var countdownTextView: TextView

    private var isAnalyzing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pullup_camera)

        previewView = findViewById(R.id.camera_preview)
        readMoreButton = findViewById(R.id.btn_read_more)
        feedback_grip = findViewById(R.id.feedback_grip)
        feedback_momentum = findViewById(R.id.feedback_momentum)
        feedback_ROM = findViewById(R.id.feedback_ROM)
        overlay = findViewById(R.id.overlay_view)
        feedback_counter = findViewById(R.id.feedback_counter)
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
                feedback_grip.text = resultBundle.predictionGrip
                feedback_momentum.text = resultBundle.predictionMomentum
                feedback_ROM.text = resultBundle.predictionROM
                overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )
                overlay.invalidate()

                if (resultBundle.predictionROM == "ROM") {
                    isPushUpDown = true
                } else if (resultBundle.predictionROM == "NO ROM" && isPushUpDown) {
                    pushUpCount++
                    isPushUpDown = false
                    feedback_counter.text = "Push-Up Count: $pushUpCount"
                }
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }
}
