# Natural Camera

A small Android-native camera app focused on one thing: **natural-looking photos without aggressive beautification, saturation, sharpening or HDR-like processing**.

The first device profile is tuned for **Infinix Hot 60 Pro (X6885)**. The device reports Camera2 `FULL` support with RAW, manual sensor, manual post-processing and burst capture.

## v0.1 goals

- Native Android / Kotlin / Jetpack Compose
- CameraX + Camera2 interop
- Native 4:3 capture
- Conservative JPEG quality pipeline
- Camera2 edge enhancement disabled where supported
- Camera2 noise reduction set to MINIMAL where supported
- Automatic highlight protection from live Y-plane sampling
- Tap focus on rear camera
- Pinch zoom
- Flash
- Front/rear camera switch
- No beauty mode
- No portrait mode
- No night mode
- No scene filters

## Natural processing philosophy

The app intentionally prefers:
- protected highlights over maximum scene brightness;
- real texture over heavy denoise;
- restrained edge enhancement over fake sharpness;
- stable color over boosted saturation.

`HighlightAnalyzer` samples the luma plane in real time. If clipped highlights occupy too much of the frame, the controller applies a small negative exposure compensation. On the Hot 60 Pro the measured AE step is 0.1 EV, so offsets -1/-2 correspond to approximately -0.1/-0.2 EV.

## Important limitation of v0.1

This version still uses CameraX's JPEG capture path. We reduce vendor processing through Camera2 request controls and protect exposure before capture, but a true sensor-to-JPEG Natural Color pipeline will require the planned YUV/RAW path.

The project structure keeps image tuning separate so that migration can happen without rebuilding the UI.

## Build locally

Requirements:
- JDK 17
- Android SDK 35
- Gradle 8.9

Run:

```bash
gradle :app:assembleDebug
```

APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Every push or pull request to `main` builds a debug APK and uploads it as the `natural-camera-debug` artifact.

## Next technical milestone

1. Test this build on X6885.
2. Capture comparison scenes against the stock Infinix camera.
3. Tune highlight thresholds/exposure behavior.
4. Add a direct YUV processing path for custom natural tone/color rendering.
5. Optionally add RAW/DNG internally only if it improves final JPEG quality.

No feature expansion is planned until the base rendering is good.
