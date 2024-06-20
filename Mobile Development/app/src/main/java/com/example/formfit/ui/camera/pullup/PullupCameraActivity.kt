package com.example.formfit.ui.camera.pullup

import android.Manifest
import android.annotation.SuppressLint
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
<<<<<<< HEAD
import com.example.formfit.ui.feedback.FeedbackActivity
=======
import com.example.formfit.ui.camera.pullup.PoseLandmarkerHelper
import com.example.formfit.ui.feedback.FeedbackPullupActivity
>>>>>>> c3ed130fc95f759b37def2485d0356155b7c7ad4
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

    private var pullUpCount = 0
    private var currentState : String = String()

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownSeconds = 10
    private lateinit var countdownTextView: TextView

    private var isAnalyzing = false
    private var startPredicting = false
    private var isUP = false

    // state sequences
    var stateSequence : MutableList<String> = mutableListOf<String>()

    // Var detail feedback
    var detailFeedback : MutableList<IntArray> = mutableListOf<IntArray>()

    var counterGrip : MutableList<Int> = mutableListOf(0,0,0)
    var counterROM : MutableList<Int> = mutableListOf(0,0)
    var counterMomentum : MutableList<Int> = mutableListOf(0,0)



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
            val intent = Intent(this, FeedbackPullupActivity::class.java)
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
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
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

    @SuppressLint("SetTextI18n")
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

                // counter Repetisi
                currentState = resultBundle.predictionState
                updateStateSequence(currentState)

                if (currentState == "down") {
                    startPredicting = true
                }

                // counter all label given
                if (startPredicting) {
                    // GRIP COUNTER
                    if (resultBundle.predictionGrip == "Posisi Grip: Benar") {
                        counterGrip[0]++
                    } else if (resultBundle.predictionGrip == "Posisi Grip: Terlalu Lebar!, Kecilkan grip anda") {
                        counterGrip[1]++
                    } else if (resultBundle.predictionGrip == "Posisi Grip: Terlalu Dekat!, Lebarkan grip anda") {
                        counterGrip[2]++
                    }

                    // ROM COUNTER
                    if (resultBundle.predictionROM == "Range of Motion : FULL ROM") {
                        counterROM[0]++
                    } else if (resultBundle.predictionROM == "Range of Motion : Kurang Naik / Kurang Turun!") {
                        counterROM[1]++
                    }

                    // Momentum Counter
                    if (resultBundle.predictionMomentum == "Mengayun : Bagus, tubuh lurus dan stabil!") {
                        counterMomentum[0]++
                    } else if (resultBundle.predictionMomentum == "Mengayun : Anda mengayun, kontrol gerakan anda!") {
                        counterMomentum[1]++
                    }
                }


                if (currentState == "down") {
                    if(stateSequence.size == 4) {

                        val idxCounterGrip = counterGrip.indexOf(counterGrip.maxOrNull())
                        val idxCounterROM = counterROM.indexOf(counterROM.maxOrNull())
                        val idxCounterMomentum = counterMomentum.indexOf(counterMomentum.maxOrNull())
                        detailFeedback.add(intArrayOf(idxCounterGrip, idxCounterROM, idxCounterMomentum))

                        Log.d(TAG, "$detailFeedback")

                        pullUpCount++
                        stateSequence.clear()
                    }
                }
                feedback_counter.text = "Jumlah Repetisi : $pullUpCount"
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStateSequence(state: String) {
        if (state == "down") {
            if (stateSequence.size == 0) {
                stateSequence.add(state)
            }
        } else if (state == "trans") {
            if (
                !("up" in stateSequence) && (stateSequence.count { it == "trans" } == 0) && (stateSequence.count { it == "down" } == 1) ||
                ("up" in stateSequence) && (stateSequence.count { it == "trans" } == 1 && (stateSequence.count { it == "down" } == 1))
                )
            {
                stateSequence.add(state)
            }
        } else if (state == "up") {
            if (!(state in stateSequence) && ("trans" in stateSequence)) {
                stateSequence.add(state)
            }
        }
    }
}
