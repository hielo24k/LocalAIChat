# LocalAI Chat 🤖

A fully on-device Android AI chat application powered by [MediaPipe LLM Inference API](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android). No cloud, no backend, no API keys. Your conversations stay on your device.

## Features

- ✅ 100% on-device inference — no internet required after model download
- ✅ Streaming responses (token-by-token display)
- ✅ Gemma 2B-IT INT4 support via MediaPipe
- ✅ Model download with progress indicator
- ✅ Configurable system prompt, temperature, max tokens, top-p
- ✅ Material 3 design with dynamic color
- ✅ GPU acceleration toggle
- ✅ Privacy-first: only INTERNET permission (for download only)

## Requirements

| Requirement | Minimum |
|---|---|
| Android | API 26 (Android 8.0+) |
| Architecture | arm64-v8a |
| RAM | 6 GB+ (12 GB recommended) |
| Storage | 2 GB free (for Gemma 2B-IT) |
| GPU | Optional but strongly recommended |

## Setup

### 1. Open in Android Studio

1. Clone or download this project
2. Open **Android Studio Ladybug** (2024.x) or newer
3. Select `File > Open` and navigate to the `LocalAIChat/` directory
4. Wait for Gradle sync to complete

### 2. Gradle Sync

If you see a sync error related to `com.google.mediapipe:tasks-genai`:

**Option A (recommended):** Verify the version in `gradle/libs.versions.toml`:
```toml
mediapipeTasksGenai = "0.10.14"
```
Check the latest version at: https://mvnrepository.com/artifact/com.google.mediapipe/tasks-genai

**Option B:** Disable MediaPipe and use the mock engine:
In `ServiceLocator.kt`, set:
```kotlin
private const val USE_MOCK_ENGINE = true
```
The app will work fully with simulated responses.

### 3. Build and Run

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

Or use Android Studio's **Run** button with a physical arm64-v8a device connected.

> ⚠️ **Physical device required.** The emulator does not support GPU delegates and may be too slow for usable inference.

### 4. Download the Model

The Gemma 2B-IT model requires accepting Google's license on Kaggle.

**Option A — In-app download:**
1. Open the app
2. Tap the 📥 icon in the top bar
3. Select your model and tap "Download Model"
4. Wait for the download to complete (~1.5 GB)

> ⚠️ The default download URL in `ModelCatalog.kt` may require authentication. If the download fails, use Option B.

**Option B — Manual download:**

1. Visit https://www.kaggle.com/models/google/gemma and accept the license
2. Download `gemma-2b-it-gpu-int4.bin` (or the `.task` bundle)
3. Transfer the file to your device:
   ```bash
   adb push gemma-2b-it-gpu-int4.bin /sdcard/Download/
   ```
4. In the app, go to **Models** → **Select from Storage** and pick the file

**Option C — Direct ADB push to app files:**
```bash
# Find your package's files dir
adb shell run-as com.localai.chat mkdir -p files/models
adb push gemma-2b-it-gpu-int4.bin /sdcard/Download/
adb shell cp /sdcard/Download/gemma-2b-it-gpu-int4.bin \
    $(adb shell run-as com.localai.chat sh -c 'echo $HOME')/files/models/
```
Then in the app: **Models** → tap "Load Model".

### 5. Using a Different Model

To replace the model with another compatible one:

1. Edit `ModelCatalog.kt`:
```kotlin
ModelInfo(
    id = "my_custom_model",
    displayName = "My Custom Model",
    description = "...",
    sizeBytes = 2_000_000_000L,
    downloadUrl = "https://your-direct-download-url/model.bin",
    fileName = "my_model.bin"
)
```

2. Compatible model formats:
   - MediaPipe `.task` bundle
   - TFLite `.bin` with LLM Inference API support
   - Gemma, Gemma 2, Falcon, Phi-2 (quantized variants)

3. Check compatibility at: https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference

## Architecture

```
app/
├── ui/
│   ├── chat/          ← ChatScreen + ChatViewModel
│   ├── models/        ← ModelScreen + ModelViewModel
│   ├── settings/      ← SettingsScreen + SettingsViewModel
│   ├── components/    ← ChatBubble, MessageInput, TypingIndicator
│   └── theme/         ← Color, Theme, Typography
├── data/
│   ├── engine/        ← InferenceEngine interface + implementations
│   ├── download/      ← ModelDownloader (OkHttp + coroutines)
│   ├── model/         ← ChatMessage, ModelInfo, ModelCatalog
│   ├── repository/    ← ChatRepository, ModelRepository
│   └── preferences/   ← AppPreferences (DataStore)
├── di/
│   └── ServiceLocator.kt
└── util/
    ├── MemoryUtils.kt
    └── FileUtils.kt
```

### Inference engine abstraction

```kotlin
interface InferenceEngine {
    fun isModelLoaded(): Boolean
    suspend fun loadModel(modelPath: String)
    suspend fun generate(prompt: String): String
    fun generateStream(prompt: String): Flow<String>  // token streaming
    fun stop()
    fun release()
}
```

To swap engines, implement this interface and update `ServiceLocator.buildEngine()`.

## Common Errors & Solutions

### Gradle sync fails: `Could not resolve com.google.mediapipe:tasks-genai`

- **Check version:** update `mediapipeTasksGenai` in `libs.versions.toml` to the latest at https://mvnrepository.com/artifact/com.google.mediapipe/tasks-genai
- **Fallback:** set `USE_MOCK_ENGINE = true` in `ServiceLocator.kt`

### App crashes on model load: `OutOfMemoryError`

- The device doesn't have enough free RAM. Close background apps.
- Switch to a smaller model (1B instead of 2B).
- Ensure `android:largeHeap="true"` is in the manifest (already set).

### Download fails: "Not enough disk space"

- Free at least 2 GB on the device storage.
- Use `adb shell df /data` to check available space.

### Download URL returns 403 or requires authentication

- The Gemma model requires Kaggle license acceptance.
- Download manually from https://www.kaggle.com/models/google/gemma and sideload.

### Model loads but responses are nonsense or empty

- Verify the model format is compatible with MediaPipe (`.task` or `.bin` LLM format).
- Try resetting the system prompt in Settings.
- Ensure you're using an instruction-tuned variant (`-it` suffix).

### `UnsatisfiedLinkError` on startup

- The app is built only for `arm64-v8a`. Do not run on `x86` emulators.
- Re-check `ndk { abiFilters += listOf("arm64-v8a") }` in `app/build.gradle.kts`.

### Streaming doesn't work, full response appears at once

- This is expected behavior if the MediaPipe version doesn't support `generateResponseAsync`.
- The `MockInferenceEngine` always streams correctly for UI testing.

## Privacy

- **INTERNET** permission is used **only** to download the model file.
- No analytics, no crash reporting, no telemetry.
- No prompts, responses, or user data leave the device.
- Conversations are stored **in memory only** and cleared when the app is killed.
- The model file is stored in `context.filesDir/models/`, inaccessible to other apps.

## License

This app code is provided as-is for educational purposes.
The Gemma model is subject to [Google's Gemma Terms of Use](https://ai.google.dev/gemma/terms).
