# Video note FOV check (#237)

This is a reproducible QA guide, **not** a claim of a resolved field-of-view bug.

1. Use the same device, physical camera lens, distance and fixed camera position. Record a grid / four landmarks near the frame edges. Disable any intentional pinch zoom.
2. Compare the BeyGram preview, the raw video-note MP4 **before** Media3 processing, the locally exported circle and the file downloaded back from Saved Messages (with explicit consent). If the raw private file is no longer available, do not claim the raw comparison happened.
3. Use square 1:1 crops of the same baseline lens. Comparing a square circle with a full 16:9 ordinary video without cropping falsely suggests a 1.2–1.3x zoom.
4. Export the diagnostics and check camera ID, front/back, first/last Camera2 SCALER_CROP_REGION, CaptureResult.CONTROL_ZOOM_RATIO (API >= 30), initial 1x requested zoom and raw/export resolutions. Also compare **CameraX Preview vs VideoCapture** `stream=...`, `crop=(x,y) WxH`, `retained=%W/%H` and `rotation` (captured after `bindToLifecycle`). These are *per-use-case output-buffer* crops, not physical sensor FOV or evidence of digital zoom. A null result means unavailable, **not** 1x.
5. On a fixed tripod, collect separate diagnostic reports for the **480×480 output (SD capture)** and **640×640 output (HD capture)** presets, using the same camera/scene. Compare the actual CameraX stream resolutions and square crop fractions with the visible grid markers, not only the requested preset. Repeat on front and rear cameras, after switching cameras (new geometry entries appear), rotation, pinch zoom, bright/dim light and supported stabilization modes; test on at least two devices.
6. Log differences without identifying a root cause prematurely. Camera2 cropRegion **does not** include all downstream ViewPort, preview surface, Media3 or Telegram transformations. A change in preview alone is not evidence of video encoding zoom.
7. Verify no regression to FPS, final side <=640, audio, orientation and mirror behavior.

CameraX references:
- https://developer.android.com/media/camera/camerax/configuration
- https://developer.android.com/reference/kotlin/androidx/camera/core/CameraControl
- https://developer.android.com/reference/androidx/camera/view/PreviewView.ScaleType
- https://developer.android.com/reference/kotlin/androidx/camera/core/ResolutionInfo
- https://developer.android.com/reference/kotlin/androidx/camera/video/VideoCapture
