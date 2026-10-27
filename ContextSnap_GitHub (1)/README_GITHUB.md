# Get the APK using GitHub (no Android Studio needed)

GitHub's servers build the app for you. You need a free GitHub account and a browser.

## Step 1 - Make an account and a repository
1. Go to github.com, click Sign up, and create a free account (verify your email).
2. Click the + at the top right -> New repository.
3. Repository name: ContextSnap. Choose Public (or Private - both work).
4. Click Create repository.

## Step 2 - Upload the project
1. Unzip ContextSnap_GitHub.zip on your computer. Open the unzipped folder.
   You should see: .github, app, addon_mobilenet, build.gradle.kts,
   gradle.properties, settings.gradle.kts, README files.
   (If you cannot see ".github", turn on View -> Show -> Hidden items in File Explorer.)
2. On your new repository page click "uploading an existing file".
3. Select EVERYTHING inside the unzipped folder (Ctrl+A) and DRAG it into the
   browser page. Do not drag the outer folder itself - drag its contents.
   Wait until every file shows as uploaded (the app folder has many files).
4. Scroll down and click Commit changes.

## Step 3 - Check the build file landed
Open the repository's Code tab and look for .github/workflows/build.yml.
If it is NOT there (some browsers skip hidden folders):
1. Click Add file -> Create new file.
2. In the name box type exactly:  .github/workflows/build.yml
   (typing the slashes creates the folders).
3. Paste the contents of build.yml from the unzipped folder into the big box.
4. Click Commit changes.

## Step 4 - Run the build
1. Click the Actions tab. If asked, click "I understand my workflows, enable them".
2. You will see "Build APK" running (yellow dot). It takes about 5-10 minutes.
   If it does not start: click Build APK on the left -> Run workflow -> Run workflow.
3. A green tick means success. A red cross means a build error.

## Step 5 - Download the APK
1. Click the finished run (the green one).
2. Scroll to the bottom to "Artifacts" and click ContextSnap-APK.
3. A zip downloads. Unzip it: inside is app-debug.apk.

## Step 6 - Install on your phone
1. Send app-debug.apk to your phone (WhatsApp to yourself, email, or USB cable).
2. Tap the file. If Android blocks it, allow "Install unknown apps" for the app you
   opened it from (Settings will take you there), then tap Install.
3. Open Context Snap. Allow Location when asked.

## If the build fails (red cross)
Click the failed run -> click the "build" job -> open the step with the red cross ->
copy the red error lines and send them to me. Most failures are one-line fixes.

## IMPORTANT: use the build.yml from THIS zip
Do not use any older build file copied from a chat message (those looked for a file called
gradlew, which this project does not have, and fail with "gradlew: No such file").
The correct file is .github/workflows/build.yml inside this zip.
If you already have an older one in your repository: open it on GitHub, click the pencil
icon, replace everything with the build.yml from this zip, and click Commit changes.

## How to copy the error if the build still fails
Actions tab -> click the run with the red cross -> click "build" on the left ->
click the step with the red cross to expand it -> copy the last 40 lines (look for
"What went wrong" or "error:") and send them to me.
