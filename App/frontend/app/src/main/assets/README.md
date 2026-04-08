## TFLite Model Setup

This app uses **EfficientDet-Lite0** for real object detection (COCO classes).

### Download
1. Go to: https://tfhub.dev/tensorflow/lite-model/efficientdet/lite0/detection/metadata/1
2. Click **Download** → get `efficientdet_lite0.tflite` (~4.4 MB)
3. Place it in: `app/src/main/assets/efficientdet_lite0.tflite`

### Without the Model
If the model file is missing, the app **automatically falls back to mock detection** (cycles through fake objects every 3 seconds). All other features (popup, onboarding, etc.) still work normally.
