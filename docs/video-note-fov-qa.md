# Video note FOV check (#237)

This is a reproducible QA guide, **not** a claim of a resolved field-of-view bug.

1. Use the same device, physical camera lens, distance and fixed camera position. Record a grid / four landmarks near the frame edges. Disable any intentional pinch zoom.
2. Compare the BeyGram preview, the raw video-note MP4 **before** Media3 processing, the locally exported circle and the file downloaded back from Saved Messages (with explicit consent). If the raw private file is no longer available, do not claim the raw comparison happened.
3. Use square 1:1 crops of the same baseline lens. Comparing a square circle with a full 16:9 ordinary video without cropping falsely suggests a 1.2–1.3x zoom.
4. Export the diagnostics and check camera ID, front/back, first/last Camera2 SCALER_CROP_REGION, CaptureResult.CONTROL_ZOOM_RATIO (API >= 30), initial 1x requested zoom and raw/export resolutions. A null result means unavailable, **not** 1x.
5. Test front and rear cameras, startup, camera switching, after intentional pinch zoom, rotation, bright/dim light and stabilization modes supported by the device. Compare the same stages on at least two devices.
6. Log differences without identifying a root cause prematurely. Camera2 cropRegion **does not** include all downstream ViewPort, preview surface, Media3 or Telegram transformations. A change in preview alone is not evidence of video encoding zoom.
7. Verify no regression to FPS, final side <=640, audio, orientation and mirror behavior.

CameraX references:
- https://developer.android.com/media/camera/camerax/configuration
- https://developer.android.com/reference/kotlin/androidx/camera/core/CameraControl
- https://developer.android.com/reference/androidx/camera/view/PreviewView.ScaleType
