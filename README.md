# CBZ Text Studio 6.3 — Android source preview

This is an Android Studio/Gradle source project, **not a precompiled APK**. It reuses the PC v6.3 editor interface and JavaScript, with a native Android local HTTP backend in Java, native document pickers, and bundled Google ML Kit OCR. The APK does not require a desktop server or Python. The editor uses localhost inside the app, not a public server. Android 7.0+ (API 24) is targeted; Android 10+ with an updated WebView and 4 GB RAM or more is suggested for practical comic editing, not a tested minimum. Large pages/chapters may run out of memory.

## Build once (Windows/Linux/macOS)

1. Install Android Studio, Android SDK Platform 34 and Build Tools 34.0.0. Use JDK 17.
2. Open this folder (the one containing settings.gradle) as a project. Use Gradle 8.7. If Android Studio requests a Gradle wrapper, use a local Gradle 8.7 installation and run `gradle wrapper --gradle-version 8.7` in the project folder. The binary wrapper JAR is not included.
3. Sync Gradle. Internet is required for the build dependencies and the bundled ML Kit models. Build a debug APK using Build > Build Bundle(s) / APK(s) > Build APK(s), or run `gradle :app:assembleDebug`.
4. APK output: `app/build/outputs/apk/debug/app-debug.apk`. Copy to your Android phone, install it, and open CBZ Text Studio. No PC connection is needed afterwards.

Alternative: upload the contents of this folder (including `.github`) to a private GitHub repository. The included GitHub Actions workflow builds an APK and uploads it as an artifact on each push or manual dispatch. Download the artifact ZIP and extract the APK. The workflow has not been run here. The debug APK is for personal testing, not a production Play Store release.

## AI and feature names

Smart AI on Android uses bundled ML Kit Text Recognition v2 (Latin, Chinese, Japanese, Korean). No separate model download after installation, no API key, no subscription. `Fast · Tesseract` and `Comic AI · hybrid` are displayed but disabled because their PC backends are not ported. `Smart AI` retains its familiar feature name but the engine is ML Kit instead of RapidOCR. The Detection method selector is retained but disabled; ML Kit decides its segmentation. Recognition is not guaranteed to be as accurate as the PC hybrid engine. Original translated images are not sent to a remote inference API by this app.

## Editing on the phone

- Open CBZ / ZIP: use Android's file picker. Use the horizontal top toolbar for + Text box, + Image, + Font, Pencil, + Shape, Pen curve, Eraser, Eyedropper, Continuous editor and preview controls.
- Bottom shortcuts: Pages, OCR / Layers / Properties / Raw, Scan region, Select area to restore, Size −/+, Font −/+, Undo, Redo, Cancel tool. Swipe the bottom bar horizontally to see more. Close a drawer before drawing on the canvas. Use the zoom slider or Fit width. Tap the selected text to type; use Properties for exact values.
- Restore from raw: load the original CBZ / ZIP, match the page, use Select area to restore. No generative image repair; the existing proportional crop and manual offsets are retained.
- Pages: add images as pages; use page up/down buttons to change order (touch drag reorder not validated). Export CBZ opens Android's save dialog. Save project and Export text also use Android's save dialog. Session autosaves in app-private storage; keep the original comics and export important work. Project JSON may contain local archive references; copying it between PC and Android, or reinstalling the app, will not automatically transfer the referenced archives.
- The original v6.3 mask and restoration ordering is reused. Raw patches are above OCR masks, below normal overlays.

## Validation / limitations

JavaScript syntax and isolated Chromium tests of the touch-layout drawer controls passed. The Android SDK/Gradle are not present in the authoring environment and dependency network access was blocked. Native Java compilation, APK signing, Android WebView upload/export, live ML Kit inference and phone performance have NOT been tested. No APK is included. OCR and export require device tests before relying on this for production. Pinch-to-zoom is not implemented; the zoom slider is available. Very tall images may exceed Android WebView canvas limits. PC-specific OCR engines and exact OCR engine parity are not implemented. No claim of full feature parity is made.
