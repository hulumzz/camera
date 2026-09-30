package id.nalaro.camera.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.provider.MediaStore
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import id.nalaro.camera.processing.DefaultNaturalTuning
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@androidx.camera.camera2.interop.ExperimentalCamera2Interop
class NaturalCameraController(
    private val context: Context
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashEnabled = false
    private var currentExposureOffset = 0

    fun start(owner: LifecycleOwner, view: PreviewView) {
        lifecycleOwner = owner
        previewView = view

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return
        val view = previewView ?: return

        provider.unbindAll()

        val previewBuilder = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
        restrainVendorProcessing(previewBuilder)
        val preview = previewBuilder.build().also {
            it.setSurfaceProvider(view.surfaceProvider)
        }

        val captureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setJpegQuality(97)
        restrainVendorProcessing(captureBuilder)
        imageCapture = captureBuilder.build()

        val analysisBuilder = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        restrainVendorProcessing(analysisBuilder)
        val analysis = analysisBuilder.build().apply {
            setAnalyzer(
                cameraExecutor,
                HighlightAnalyzer(DefaultNaturalTuning) { offset ->
                    applyExposureOffset(offset)
                }
            )
        }

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        camera = provider.bindToLifecycle(owner, selector, preview, imageCapture, analysis)
        applyExposureOffset(0)
        applyFlash()
    }

    private fun <T> restrainVendorProcessing(builder: T) {
        when (builder) {
            is Preview.Builder -> Camera2Interop.Extender(builder).apply {
                setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
                setCaptureRequestOption(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL
                )
            }
            is ImageCapture.Builder -> Camera2Interop.Extender(builder).apply {
                setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
                setCaptureRequestOption(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL
                )
            }
            is ImageAnalysis.Builder -> Camera2Interop.Extender(builder).apply {
                setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
                setCaptureRequestOption(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL
                )
            }
        }
    }

    private fun applyExposureOffset(offset: Int) {
        val activeCamera = camera ?: return
        val state = activeCamera.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return

        val target = offset.coerceIn(
            state.exposureCompensationRange.lower,
            state.exposureCompensationRange.upper
        )
        if (target == currentExposureOffset) return

        currentExposureOffset = target
        activeCamera.cameraControl.setExposureCompensationIndex(target)
    }

    fun tapToFocus(x: Float, y: Float) {
        val view = previewView ?: return
        val activeCamera = camera ?: return
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) return

        val point = view.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()

        activeCamera.cameraControl.startFocusAndMetering(action)
    }

    fun setZoomRatio(ratio: Float) {
        val activeCamera = camera ?: return
        val zoom = activeCamera.cameraInfo.zoomState.value ?: return
        activeCamera.cameraControl.setZoomRatio(ratio.coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio))
    }

    fun currentZoomRange(): ClosedFloatingPointRange<Float> {
        val zoom = camera?.cameraInfo?.zoomState?.value
        return if (zoom == null) 1f..1f else zoom.minZoomRatio..zoom.maxZoomRatio
    }

    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindUseCases()
    }

    fun toggleFlash(): Boolean {
        flashEnabled = !flashEnabled
        applyFlash()
        return flashEnabled
    }

    private fun applyFlash() {
        imageCapture?.flashMode = if (flashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    fun takePhoto(
        onSaved: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val capture = imageCapture ?: return

        val name = "NAT_" + SimpleDateFormat(
            "yyyyMMdd_HHmmss_SSS",
            Locale.US
        ).format(System.currentTimeMillis())

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NaturalCamera")
            }
        }

        val output = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSaved()
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    fun release() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
