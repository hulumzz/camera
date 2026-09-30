package id.nalaro.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.view.PreviewView
import id.nalaro.camera.camera.NaturalCameraController

@Composable
fun CameraApp() {
    MaterialTheme {
        val context = LocalContext.current
        var granted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted = it }

        LaunchedEffect(Unit) {
            if (!granted) launcher.launch(Manifest.permission.CAMERA)
        }

        if (granted) {
            CameraScreen()
        } else {
            PermissionScreen { launcher.launch(Manifest.permission.CAMERA) }
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera permission is required", color = Color.White)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest) { Text("Allow camera") }
        }
    }
}

@Composable
private fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { NaturalCameraController(context.applicationContext) }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var shooting by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        onDispose { controller.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val range = controller.currentZoomRange()
                        zoomRatio = (zoomRatio * zoom).coerceIn(range.start, range.endInclusive)
                        controller.setZoomRatio(zoomRatio)
                    }
                },
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    setOnTouchListener { _, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            controller.tapToFocus(event.x, event.y)
                        }
                        true
                    }
                    previewView = this
                    controller.start(lifecycleOwner, this)
                }
            }
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 52.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SmallControl(
                text = if (flashOn) "Flash On" else "Flash",
                onClick = { flashOn = controller.toggleFlash() }
            )
            Text(
                text = "NATURAL",
                color = Color.White,
                modifier = Modifier.padding(top = 12.dp)
            )
            SmallControl(text = "4:3", onClick = {})
        }

        Text(
            text = if (zoomRatio < 1.05f) "1×" else String.format("%.1f×", zoomRatio),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 152.dp)
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(Modifier.size(58.dp))

            Button(
                modifier = Modifier.size(78.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    disabledContainerColor = Color.LightGray
                ),
                enabled = !shooting,
                onClick = {
                    shooting = true
                    controller.takePhoto(
                        onSaved = {
                            shooting = false
                            Toast.makeText(context, "Photo saved", Toast.LENGTH_SHORT).show()
                        },
                        onError = {
                            shooting = false
                            Toast.makeText(
                                context,
                                it.message ?: "Capture failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            ) {}

            SmallControl(
                text = "Flip",
                onClick = {
                    zoomRatio = 1f
                    controller.switchCamera()
                }
            )
        }
    }
}

@Composable
private fun SmallControl(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black.copy(alpha = 0.42f),
            contentColor = Color.White
        )
    ) {
        Text(text)
    }
}
