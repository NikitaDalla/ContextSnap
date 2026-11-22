# Build the APK on GitHub (one zip upload)

## 1. Upload the zip (do NOT unzip it)
1. Open your repository on github.com -> Add file -> Upload files.
2. Drag the file ContextSnap_GitHub.zip itself into the page. Commit changes.
   (One file cannot lose pieces on the way, unlike a folder with many files.)

## 2. Put the build file in place (only once, or when it changes)
1. In the repository open .github/workflows/build.yml -> pencil icon.
   If it does not exist: Add file -> Create new file -> name it .github/workflows/build.yml
2. Replace everything with the build.yml from inside this zip (open it in Notepad,
   copy all, paste). Commit changes. A build starts.

## 3. Download the APK
Actions tab -> click the newest run -> when it has a green tick, scroll down to
Artifacts -> ContextSnap-APK -> unzip -> app-debug.apk.
Send it to your phone, UNINSTALL the old Context Snap first, then install it.

## 4. First start on the phone - allow the permissions
Location, Physical activity (needed for the step counter) and Body sensors (heart rate).

## If the build fails
Open the failed run's summary page: the "Build errors" section shows the error lines.
Send them to me.

## If photos say "MobileNet model could not be loaded"
The build normally downloads the model by itself (see the "Download the MobileNet model"
step in the run log). If that step shows a warning, add the model by hand:
1. Download any MobileNet image-classification .tflite model WITH metadata
   (search "MobileNet V2 image classification tflite metadata" on tfhub.dev / Kaggle Models).
2. In the repository: Add file -> Upload files, upload it to app/src/main/assets/ and name it exactly
   mobilenet.tflite (if the zip is unpacked by the build, put it in the zip instead:
   unzip, add app/src/main/assets/mobilenet.tflite, zip again, upload the new zip).
