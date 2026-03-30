# YouTube Transcript Downloader

A simple Android app that downloads and displays transcripts (captions) from YouTube videos — no API key required.

## Features

- Paste any YouTube URL (`youtube.com/watch?v=...`, `youtu.be/...`, `youtube.com/shorts/...`) or a bare 11-character video ID
- Fetches captions without requiring a Google/YouTube API key
- Displays transcript entries with timestamps (`M:SS` or `H:MM:SS`)
- Prefers English captions; falls back to the first available language
- **Share** the full transcript as plain text via the system share sheet
- **Save** the transcript as a `.txt` file to the device Downloads folder
- Handles errors gracefully: invalid URL, no captions, network failure

## How it works

No API key is needed. The app:
1. Fetches the YouTube watch page HTML
2. Extracts the embedded `ytInitialPlayerResponse` JSON (contains caption track metadata)
3. Picks the best caption track URL (prefers English)
4. Fetches the TimedText XML from that URL
5. Parses it into timestamped entries and displays them in a list

## Requirements

| Tool | Version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer |
| Android SDK | compileSdk 34, minSdk 24 |
| Java | 8+ (project targets JVM 1.8) |
| Gradle | 8.4 (via wrapper — no local install needed) |

## Build

Clone the repo and build a debug APK:

```bash
git clone <repo-url>
cd claude-test-app
./gradlew assembleDebug
```

The APK is output to:
```
app/build/outputs/apk/debug/app-debug.apk
```

For a release build (requires signing config):
```bash
./gradlew assembleRelease
```

## Run / Deploy

### Via ADB (command line)

Connect a device or start an emulator (API 24+), then:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

To install and launch immediately:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.youtubetranscript/.MainActivity
```

### Via Android Studio

1. Open the project root in Android Studio
2. Select a device or emulator from the toolbar
3. Click **Run** (or press `Shift+F10`)

### Manual install

Transfer `app-debug.apk` to the device and open it from a file manager. Enable *Install from unknown sources* in device settings if prompted.

## Testing

There are no automated tests in this project. Use the following manual checklist:

| Test case | Expected result |
|---|---|
| Paste `https://www.youtube.com/watch?v=dQw4w9WgXcQ` and tap **Fetch Transcript** | Transcript loads with timestamps |
| Paste bare video ID `dQw4w9WgXcQ` | Same as above |
| Paste `https://youtu.be/dQw4w9WgXcQ` | Same as above |
| Tap **Share** after transcript loads | System share sheet opens with plain-text transcript |
| Tap **Save** after transcript loads | Toast confirms save; file appears in Downloads |
| Paste a video ID with no captions | Error: "No captions available for this video." |
| Paste an invalid string (e.g. `hello`) | Error: "Invalid YouTube URL or video ID." |
| Turn off Wi-Fi/data and fetch | Error: "Network error. Please check your connection." |
| Rotate device after transcript loads | Transcript is preserved (ViewModel survives rotation) |

## Project structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/youtubetranscript/
│   ├── TranscriptEntry.kt       # Data class; formats start time as timestamp string
│   ├── TranscriptParser.kt      # Extracts video ID, parses watch page HTML and caption XML
│   ├── TranscriptRepository.kt  # OkHttp network calls; returns sealed TranscriptResult
│   ├── TranscriptViewModel.kt   # Coroutine scope, LiveData UiState, formatted-text cache
│   ├── TranscriptAdapter.kt     # RecyclerView ListAdapter with DiffUtil
│   └── MainActivity.kt          # Single activity: UI, share, save to Downloads
└── res/
    ├── layout/activity_main.xml   # URL input, fetch button, transcript list, share/save
    ├── layout/item_transcript.xml # One row: timestamp + caption text
    └── values/
        ├── strings.xml
        ├── colors.xml
        └── themes.xml
```

## Known limitations

- **YouTube HTML changes**: The app parses `ytInitialPlayerResponse` from the watch page. If YouTube changes this structure the caption extraction will fail with "No captions available."
- **EU consent screen**: In some regions YouTube redirects to a consent page; the app may not be able to extract captions in that case.
- **Age-restricted / private videos**: These do not expose caption tracks in the page HTML and will show "No captions available."
- **Auto-generated vs. manual captions**: The app does not distinguish between them; both are fetched.
- **Language selection**: Only one caption track is fetched per request (English preferred). There is no in-app language picker.
