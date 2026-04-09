## ONNX Model Setup

This app uses a custom **YOLO torso detection model** (`best_model_torso.onnx`) exported from
Ultralytics YOLOv8 via `Computer Vision/convert_onnx.py`.

### Model Location
The model is stored in `App/frontend/models/` and is picked up automatically via the
`assets.srcDirs("../models")` entry in `app/build.gradle.kts`.

| File | Size |
|---|---|
| `best_model_torso.onnx` | ~10.6 MB |

### Inference Library
- **ONNX Runtime for Android** — `com.microsoft.onnxruntime:onnxruntime-android:1.20.0`
- Inference class: `OnnxObjectAnalyzer.kt`

### Without the Model
If no `.onnx` file is found in assets, the app **automatically falls back to mock detection**
(cycles through fake objects every 3 seconds). All other features (popup, onboarding, TTS, etc.)
continue to work normally.
