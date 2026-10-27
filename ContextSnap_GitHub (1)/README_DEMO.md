# Context Snap - Lean Demo

Needs NO extra libraries (no Room, CameraX or TensorFlow), so there is nothing
extra to download in Gradle. Package name must be: com.example.contextsnap

## Install steps (Android Studio)
1. New Project -> Empty Views Activity, Name: ContextSnap,
   Package: com.example.contextsnap, Kotlin, Min SDK 24. Wait for the Gradle sync.
2. If you copied files from the EARLIER zip, delete the folders data, auth, ui, ml
   and all old layout files first. Remove any lines you added to build.gradle.kts
   (room, kapt, camera, tensorflow, jbcrypt, security-crypto) - not needed now.
3. Delete MainActivity.kt and res/layout/activity_main.xml.
4. Copy the 10 .kt files from app/src/main/java/com/example/contextsnap into
   YOUR project's app/src/main/java/com/example/contextsnap (same folder as
   where MainActivity.kt was). No sub-folders.
5. Copy the 7 files from res/layout into your project's res/layout.
6. Replace your AndroidManifest.xml with the one here. If your project name is not
   "ContextSnap", change @style/Theme.ContextSnap to the theme name that was in your
   original manifest.
7. Plug in the phone -> press Run.

## Get the APK file
Build menu -> Generate App Bundles or APKs -> Generate APKs (or Build APK(s)).
Click "locate" in the popup. File: app/build/outputs/apk/debug/app-debug.apk
Send it to your phone and tap to install (allow "install unknown apps").

## Add MobileNet (real deep learning) - only after the demo runs
1. build.gradle.kts -> dependencies { } add:
   implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
   and inside android { } add:   androidResources { noCompress += "tflite" }
2. Create folder app/src/main/assets and put a MobileNet image-classification
   .tflite model WITH metadata in it, named exactly mobilenet.tflite.
3. Replace Labeler.kt with addon_mobilenet/Labeler.kt. Sync, Run.

## GPS and fingerprint (added)
- GPS Location appears as the LAST row in the sensor list. On first open Android asks for
  Location permission - tap Allow. GPS needs a clear sky: test outdoors or near a window.
  Tick its box and press Start to save latitude, longitude and altitude with a timestamp.
- Fingerprint login: log in with your password once, then on the dashboard press
  "Enable fingerprint login" and scan your finger. After Logout, the Login screen shows a
  "Login with fingerprint" button. Needs Android 9+ and a fingerprint already set up in
  the phone's Settings. The password login always still works.
