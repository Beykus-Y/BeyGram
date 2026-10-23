# Video note FOV check (#237)

This is a reproducible QA guide, **not** a claim of a resolved field-of-view bug.

## HD 4:3 capture candidate (PR for #237)

At recording startup, BeyGram now **prefers an HD 4:3 Recorder** when the normal
CameraX FPS decision is at most 30 FPS. For a centered 1:1 crop, this can preserve
more of the sensor's horizontal field than a 16:9 HD recording. This does **not**
digitally zoom out or switch to a different physical lens; the actual field of
view still depends on the selected stream and camera sensor.

- The chosen quality remains HD: do not upscale a 480p recording to claim 640p.
- A normal 50–60 FPS request is **not** forced to 4:3; incompatible high-speed
  combinations are not silently converted.
- CameraX can reject a quality/aspect pair at `bindToLifecycle`. An unsupported
  4:3 pair is retried **once before recording starts** using the prior default
  aspect; other errors (e.g. permission denied) remain errors.
- Diagnostics distinguish a preferred 4:3 source from fallback to default and
  preserve the actual Preview/VideoCapture stream and crop rectangles.
- The PR must not close #237 without device comparisons, nor claim improvement
  just because a 4:3 request was accepted by CameraX.


1. Use the same device, physical camera lens, distance and fixed camera position. Record a grid / four landmarks near the frame edges. Disable any intentional pinch zoom.
2. Compare the BeyGram preview, the raw video-note MP4 **before** Media3 processing, the locally exported circle and the file downloaded back from Saved Messages (with explicit consent). If the raw private file is no longer available, do not claim the raw comparison happened.
3. Use square 1:1 crops of the same baseline lens. Comparing a square circle with a full 16:9 ordinary video without cropping falsely suggests a 1.2–1.3x zoom.
4. Export the diagnostics and check camera ID, front/back, first/last Camera2 SCALER_CROP_REGION, CaptureResult.CONTROL_ZOOM_RATIO (API >= 30), initial 1x requested zoom and raw/export resolutions. Also compare **CameraX Preview vs VideoCapture** `stream=...`, `crop=(x,y) WxH`, `retained=%W/%H` and `rotation` (captured after `bindToLifecycle`). These are *per-use-case output-buffer* crops, not physical sensor FOV or evidence of digital zoom. A null result means unavailable, **not** 1x.
5. On a fixed tripod, collect separate diagnostic reports for the **480×480 output (SD capture)** and **640×640 output (HD capture)** presets, using the same camera/scene. Also compare an earlier 16:9-HD build with this 4:3-HD build on the same device; capture the reported preferred/default/fallback mode. Compare the actual CameraX stream resolutions and square crop fractions with the visible grid markers, not only the requested preset. Repeat on front and rear cameras, after switching cameras (new geometry entries appear), rotation, pinch zoom, bright/dim light and supported stabilization modes; test on at least two devices.
6. Log differences without identifying a root cause prematurely. Camera2 cropRegion **does not** include all downstream ViewPort, preview surface, Media3 or Telegram transformations. A change in preview alone is not evidence of video encoding zoom.
7. Verify no regression to FPS, final side <=640, audio, orientation and mirror behavior.

CameraX references:
- https://developer.android.com/media/camera/camerax/configuration
- https://developer.android.com/reference/kotlin/androidx/camera/core/CameraControl
- https://developer.android.com/reference/androidx/camera/view/PreviewView.ScaleType
- https://developer.android.com/reference/kotlin/androidx/camera/core/ResolutionInfo
- https://developer.android.com/reference/kotlin/androidx/camera/video/VideoCapture
