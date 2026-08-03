<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/9f5448bb-aa4e-4f3c-8b0a-70bcb86ffd63

## Real audio capture & transcription

The app records lectures with the device microphone (`MediaRecorder`, AAC in an
`.m4a` container at 64 kbps mono) and transcribes them by sending the audio
inline to Gemini (`inline_data` base64 in `generateContent`).

- The app requests the `RECORD_AUDIO` runtime permission when you tap **Record**.
  If denied, you can still import an audio file or paste a transcript manually.
- Imported files are copied into app storage, probed for their real duration
  (`MediaMetadataRetriever`), and transcribed with the same pipeline.
- The Gemini API caps requests at 20 MB total. The app rejects audio larger
  than 18 MB with an actionable message (at 64 kbps, ~35 minutes of speech fits).
  For longer material, split the recording — a Files-API upload path is the
  natural future upgrade.
- Without a `GEMINI_API_KEY` the app still runs: summarization falls back to an
  offline demo generator, while audio transcription reports that a key is
  required.

## Tests

`./gradlew testDebugUnitTest` runs the local unit test suite (JUnit 4 +
Robolectric + kotlinx-coroutines-test): Gemini JSON/parsing contracts, Room
repository on in-memory SQLite, and ViewModel flows driving a real database
with a fake microphone controller. No device or API key required.

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
