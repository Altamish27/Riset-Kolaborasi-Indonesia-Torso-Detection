import cv2
import os
from datetime import datetime
import time

# Daftar class untuk labeling
CLASSES = [
    "Otak",
    "Kepala",
    "Tenggorokan",
    "Dada_Luar",
    "Dada_Dalam",
    "Rusuk",
    "Paru-Paru_Kanan",
    "Paru-paru_Kiri",
    "Jantung",
    "Ginjal_Luar",
    "Ginjal_Dalam",
    "Hati",
    "Lambung",
    "Usus",
    "Penis",
    "Vagina"
]

# Variabel global
drawing = False
ix, iy = -1, -1
fx, fy = -1, -1
current_class = 0
bboxes = []  # List bounding boxes: [(class_id, x1, y1, x2, y2), ...]
frozen_frame = None
is_frozen = False
auto_capture = False
last_capture_time = 0
capture_interval = 2.0  # Interval capture dalam detik (default 2 detik)

def draw_rectangle(event, x, y, flags, param):
    """Callback untuk menggambar bounding box"""
    global ix, iy, fx, fy, drawing, frozen_frame, display_frame
    
    if not is_frozen:
        return
    
    if event == cv2.EVENT_LBUTTONDOWN:
        drawing = True
        ix, iy = x, y
    
    elif event == cv2.EVENT_MOUSEMOVE:
        if drawing:
            display_frame = frozen_frame.copy()
            # Gambar bbox yang sedang dibuat
            cv2.rectangle(display_frame, (ix, iy), (x, y), (0, 255, 0), 2)
            # Gambar bbox yang sudah ada
            for bbox in bboxes:
                cls_id, x1, y1, x2, y2 = bbox
                color = (255, 0, 0) if cls_id == current_class else (0, 0, 255)
                cv2.rectangle(display_frame, (x1, y1), (x2, y2), color, 2)
                cv2.putText(display_frame, CLASSES[cls_id], (x1, y1-5),
                           cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 2)
    
    elif event == cv2.EVENT_LBUTTONUP:
        drawing = False
        fx, fy = x, y
        if abs(fx - ix) > 5 and abs(fy - iy) > 5:  # Minimal ukuran bbox
            x1, y1 = min(ix, fx), min(iy, fy)
            x2, y2 = max(ix, fx), max(iy, fy)
            bboxes.append((current_class, x1, y1, x2, y2))
            print(f"  ✓ Bbox ditambahkan: {CLASSES[current_class]} ({x1}, {y1}, {x2}, {y2})")

def convert_to_yolo(bbox, img_width, img_height):
    """Convert bbox ke format YOLO"""
    class_id, x1, y1, x2, y2 = bbox
    
    # Calculate center, width, height (normalized)
    x_center = ((x1 + x2) / 2) / img_width
    y_center = ((y1 + y2) / 2) / img_height
    width = (x2 - x1) / img_width
    height = (y2 - y1) / img_height
    
    return f"{class_id} {x_center:.6f} {y_center:.6f} {width:.6f} {height:.6f}"

def save_annotation(image, bboxes, output_dir_images, output_dir_labels):
    """Save image dan label dalam format YOLO"""
    if len(bboxes) == 0:
        print("  ⚠ Tidak ada bounding box, skip save")
        return
    
    # Generate timestamp filename
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
    img_filename = f"img_{timestamp}.jpg"
    label_filename = f"img_{timestamp}.txt"
    
    img_path = os.path.join(output_dir_images, img_filename)
    label_path = os.path.join(output_dir_labels, label_filename)
    
    # Save image
    cv2.imwrite(img_path, image)
    
    # Save label (YOLO format)
    height, width = image.shape[:2]
    with open(label_path, 'w') as f:
        for bbox in bboxes:
            yolo_line = convert_to_yolo(bbox, width, height)
            f.write(yolo_line + '\n')
    
    print(f"  ✓ SAVED: {img_filename} dengan {len(bboxes)} bbox")
    print(f"    - Image: {img_path}")
    print(f"    - Label: {label_path}")

def main():
    global current_class, frozen_frame, is_frozen, bboxes, display_frame
    global auto_capture, last_capture_time, capture_interval
    
    # Setup folders
    output_dir = "dataset_organ_yolo"
    output_dir_images = os.path.join(output_dir, "images")
    output_dir_labels = os.path.join(output_dir, "labels")
    os.makedirs(output_dir_images, exist_ok=True)
    os.makedirs(output_dir_labels, exist_ok=True)
    
    # Save classes.txt
    classes_file = os.path.join(output_dir, "classes.txt")
    with open(classes_file, 'w') as f:
        for cls in CLASSES:
            f.write(cls + '\n')
    print(f"Classes saved: {classes_file}\n")
    
    # Akses kamera
    print("Mengakses kamera DroidCam...")
    cap = None
    
    # Coba berbagai metode
    for method in [(1, None), (0, None), (1, cv2.CAP_DSHOW), (0, cv2.CAP_DSHOW)]:
        idx, backend = method
        print(f"  Mencoba index {idx}...")
        
        if backend:
            cap = cv2.VideoCapture(idx, backend)
        else:
            cap = cv2.VideoCapture(idx)
        
        if cap.isOpened():
            # Test baca frame
            ret, test_frame = cap.read()
            if ret and test_frame is not None:
                print(f"  ✓ Berhasil dengan index {idx}!")
                break
            else:
                cap.release()
                cap = None
        else:
            cap = None
    
    if cap is None or not cap.isOpened():
        print("❌ Gagal mengakses kamera!")
        print("Pastikan DroidCam sudah connect!")
        input("Tekan Enter untuk keluar...")
        return
    
    # Set resolusi
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1920)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 1080)
    
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    
    print(f"✓ Kamera aktif! Resolusi: {width}x{height}\n")
    print("="*70)
    print("KONTROL:")
    print("  C           - Toggle AUTO CAPTURE mode (capture otomatis)")
    print("  +/-         - Ubah interval auto capture (detik)")
    print("  SPACE       - Manual capture frame untuk labeling")
    print("  Mouse Drag  - Gambar bounding box")
    print("  A/D         - Ganti class (prev/next)")
    print("  1-9,0       - Pilih class langsung")
    print("  BACKSPACE   - Hapus bbox terakhir")
    print("  ENTER       - Save annotation & lanjut frame baru")
    print("  ESC         - Cancel (kembali ke live view)")
    print("  Q           - Keluar aplikasi")
    print("="*70)
    
    cv2.namedWindow('Auto Capture Labeling YOLO')
    cv2.setMouseCallback('Auto Capture Labeling YOLO', draw_rectangle)
    
    frame_count = 0
    saved_count = 0
    
    while True:
        current_time = time.time()
        
        # Mode normal: tampilkan live feed
        if not is_frozen:
            ret, frame = cap.read()
            if not ret:
                print("Error membaca frame")
                break
            
            display_frame = frame.copy()
            
            # Auto capture jika mode aktif
            if auto_capture and (current_time - last_capture_time) >= capture_interval:
                # Auto freeze frame
                frozen_frame = frame.copy()
                is_frozen = True
                bboxes = []
                last_capture_time = current_time
                frame_count += 1
                print(f"\n[AUTO CAPTURE #{frame_count}] Siap untuk labeling...")
                print(f"  Class aktif: {CLASSES[current_class]}")
            
            # Tampilkan info
            info_y = 30
            cv2.putText(display_frame, "LIVE VIEW - Tekan C untuk auto capture", 
                       (10, info_y), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
            
            if auto_capture:
                cv2.circle(display_frame, (width - 30, 30), 10, (0, 0, 255), -1)
                cv2.putText(display_frame, f"AUTO: {capture_interval:.1f}s", 
                           (width - 150, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)
            
            cv2.putText(display_frame, f"Saved: {saved_count}", 
                       (10, height - 20), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)
        
        # Mode frozen: labeling
        else:
            # Gambar semua bbox yang ada
            display_frame = frozen_frame.copy()
            for bbox in bboxes:
                cls_id, x1, y1, x2, y2 = bbox
                color = (255, 0, 0) if cls_id == current_class else (0, 0, 255)
                cv2.rectangle(display_frame, (x1, y1), (x2, y2), color, 2)
                cv2.putText(display_frame, CLASSES[cls_id], (x1, y1-5),
                           cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 2)
            
            # Info
            cv2.putText(display_frame, "LABELING MODE - Drag untuk bbox", 
                       (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
            cv2.putText(display_frame, f"Class: [{current_class}] {CLASSES[current_class]}", 
                       (10, 60), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
            cv2.putText(display_frame, f"Bboxes: {len(bboxes)}", 
                       (10, 90), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
        
        cv2.imshow('Auto Capture Labeling YOLO', display_frame)
        
        # Keyboard input (30 FPS = ~30ms delay untuk smooth display)
        key = cv2.waitKey(30) & 0xFF
        
        # C - Toggle auto capture
        if key == ord('c') or key == ord('C'):
            auto_capture = not auto_capture
            status = "AKTIF" if auto_capture else "NONAKTIF"
            print(f"\n{'='*50}")
            print(f"Auto Capture: {status}")
            if auto_capture:
                print(f"Interval: {capture_interval} detik")
                last_capture_time = current_time
            print(f"{'='*50}\n")
        
        # +/- Ubah interval
        elif key == ord('+') or key == ord('='):
            capture_interval = min(10.0, capture_interval + 0.5)
            print(f"Interval: {capture_interval} detik")
        elif key == ord('-') or key == ord('_'):
            capture_interval = max(0.5, capture_interval - 0.5)
            print(f"Interval: {capture_interval} detik")
        
        # SPACE - Manual freeze
        elif key == ord(' ') and not is_frozen:
            ret, frame = cap.read()
            if ret:
                frozen_frame = frame.copy()
                is_frozen = True
                bboxes = []
                frame_count += 1
                print(f"\n[MANUAL CAPTURE #{frame_count}]")
                print(f"  Class aktif: {CLASSES[current_class]}")
        
        # A/D - Ganti class
        elif key == ord('a') or key == ord('A'):
            current_class = (current_class - 1) % len(CLASSES)
            print(f"  Class: {CLASSES[current_class]}")
        elif key == ord('d') or key == ord('D'):
            current_class = (current_class + 1) % len(CLASSES)
            print(f"  Class: {CLASSES[current_class]}")
        
        # 1-9, 0 - Pilih class langsung
        elif key >= ord('0') and key <= ord('9'):
            num = int(chr(key))
            if num == 0:
                num = 10
            if num - 1 < len(CLASSES):
                current_class = num - 1
                print(f"  Class: {CLASSES[current_class]}")
        
        # BACKSPACE - Hapus bbox terakhir
        elif key == 8 and is_frozen and len(bboxes) > 0:  # Backspace
            removed = bboxes.pop()
            print(f"  ✗ Bbox dihapus: {CLASSES[removed[0]]}")
        
        # ENTER - Save dan lanjut
        elif key == 13 and is_frozen:  # Enter
            save_annotation(frozen_frame, bboxes, output_dir_images, output_dir_labels)
            saved_count += 1
            is_frozen = False
            bboxes = []
        
        # ESC - Cancel
        elif key == 27 and is_frozen:  # ESC
            print("  ✗ Labeling dibatalkan")
            is_frozen = False
            bboxes = []
        
        # Q - Quit
        elif key == ord('q') or key == ord('Q'):
            break
    
    cap.release()
    cv2.destroyAllWindows()
    
    print(f"\n{'='*70}")
    print(f"SELESAI!")
    print(f"Total frame captured: {frame_count}")
    print(f"Total tersimpan: {saved_count}")
    print(f"Output: {output_dir}/")
    print(f"{'='*70}")

if __name__ == "__main__":
    main()
