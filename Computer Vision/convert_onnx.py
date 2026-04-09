from ultralytics import YOLO

print("Loading model...")
model = YOLO("best_model_torso.pt")

print("Converting model to ONNX...")

model.export(format="onnx", simplify=True)

print("Model exported successfully")