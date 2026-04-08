import cv2
import os
import glob

# Variabel global untuk mouse callback
drawing = False
ix, iy = -1, -1
fx, fy = -1, -1
bbox = None

def draw_rectangle(event, x, y, flags, param):
    """Callback function untuk menggambar rectangle dengan mouse"""
    global ix, iy, fx, fy, drawing, bbox, img_copy
    
    if event == cv2.EVENT_LBUTTONDOWN:
        drawing = True
        ix, iy = x, y
    
    elif event == cv2.EVENT_MOUSEMOVE:
        if drawing:
            img_copy = img.copy()
            cv2.rectangle(img_copy, (ix, iy), (x, y), (0, 255, 0), 2)
            cv2.imshow('Buat Bounding Box', img_copy)
    
    elif event == cv2.EVENT_LBUTTONUP:
        drawing = False
        fx, fy = x, y
        cv2.rectangle(img_copy, (ix, iy), (fx, fy), (0, 255, 0), 2)
        cv2.imshow('Buat Bounding Box', img_copy)
        bbox = (min(ix, fx), min(iy, fy), max(ix, fx), max(iy, fy))


def get_bbox_interactive(image_path):
    """Dapatkan bounding box secara interaktif dari user"""
    global img, img_copy, bbox
    
    img = cv2.imread(image_path)
    if img is None:
        print(f"Error: Tidak bisa membuka gambar {image_path}")
        return None
    
    # Resize jika gambar terlalu besar
    height, width = img.shape[:2]
    max_height = 800
    if height > max_height:
        scale = max_height / height
        new_width = int(width * scale)
        new_height = int(height * scale)
        display_img = cv2.resize(img, (new_width, new_height))
    else:
        display_img = img.copy()
        scale = 1.0
    
    img = display_img
    img_copy = img.copy()
    
    cv2.namedWindow('Buat Bounding Box')
    cv2.setMouseCallback('Buat Bounding Box', draw_rectangle)
    
    print("\n=== PETUNJUK ===")
    print("1. Klik dan drag mouse untuk membuat bounding box di sekitar objek")
    print("2. Tekan ENTER jika sudah selesai")
    print("3. Tekan 'r' untuk reset/ulang")
    print("4. Tekan 'q' untuk quit/batal")
    print("================\n")
    
    while True:
        cv2.imshow('Buat Bounding Box', img_copy)
        key = cv2.waitKey(1) & 0xFF
        
        if key == 13:  # ENTER
            if bbox is not None:
                break
            else:
                print("Buat bounding box terlebih dahulu!")
        elif key == ord('r'):  # Reset
            img_copy = img.copy()
            bbox = None
        elif key == ord('q'):  # Quit
            cv2.destroyAllWindows()
            return None
    
    cv2.destroyAllWindows()
    
    # Adjust bbox jika gambar di-resize
    if scale != 1.0:
        bbox = tuple(int(coord / scale) for coord in bbox)
    
    return bbox


def bbox_to_yolo_format(bbox, img_width, img_height):
    """
    Konversi bbox (x1, y1, x2, y2) ke format YOLO (center_x, center_y, width, height)
    Semua nilai dinormalisasi antara 0-1
    """
    x1, y1, x2, y2 = bbox
    
    # Hitung center, width, height
    center_x = (x1 + x2) / 2.0
    center_y = (y1 + y2) / 2.0
    width = x2 - x1
    height = y2 - y1
    
    # Normalisasi
    center_x /= img_width
    center_y /= img_height
    width /= img_width
    height /= img_height
    
    return center_x, center_y, width, height


def create_yolo_annotations(images_folder, output_folder, class_id=1):
    """
    Buat anotasi YOLO untuk semua gambar
    """
    # Buat folder output jika belum ada
    if not os.path.exists(output_folder):
        os.makedirs(output_folder)
    
    # Dapatkan semua gambar
    image_files = sorted(glob.glob(os.path.join(images_folder, "*.jpg")))
    image_files += sorted(glob.glob(os.path.join(images_folder, "*.png")))
    
    if len(image_files) == 0:
        print(f"Error: Tidak ada gambar di folder {images_folder}")
        return
    
    print(f"Ditemukan {len(image_files)} gambar")
    
    # Gunakan gambar pertama untuk membuat bounding box
    first_image = image_files[0]
    print(f"\nMenggunakan gambar: {os.path.basename(first_image)}")
    
    # Dapatkan bounding box dari user
    bbox = get_bbox_interactive(first_image)
    
    if bbox is None:
        print("Dibatalkan!")
        return
    
    print(f"\nBounding Box: {bbox}")
    
    # Baca dimensi gambar asli
    img = cv2.imread(first_image)
    img_height, img_width = img.shape[:2]
    
    # Konversi ke format YOLO
    yolo_bbox = bbox_to_yolo_format(bbox, img_width, img_height)
    print(f"YOLO Format (class_id, center_x, center_y, width, height): {class_id}, {yolo_bbox}")
    
    # Tanya user apakah ingin melanjutkan
    print(f"\n{'='*50}")
    print(f"Akan membuat anotasi untuk {len(image_files)} gambar")
    print(f"Class ID: {class_id}")
    print(f"Bounding Box: {bbox}")
    print(f"{'='*50}")
    response = input("Lanjutkan? (y/n): ")
    
    if response.lower() != 'y':
        print("Dibatalkan!")
        return
    
    # Buat anotasi untuk semua gambar
    print("\nMembuat anotasi...")
    for i, image_file in enumerate(image_files):
        # Baca gambar untuk mendapatkan dimensi (jika berbeda)
        img = cv2.imread(image_file)
        if img is None:
            print(f"Warning: Tidak bisa membaca {image_file}, dilewati")
            continue
        
        img_height, img_width = img.shape[:2]
        yolo_bbox = bbox_to_yolo_format(bbox, img_width, img_height)
        
        # Nama file anotasi (sama dengan nama gambar tapi .txt)
        base_name = os.path.splitext(os.path.basename(image_file))[0]
        annotation_file = os.path.join(output_folder, f"{base_name}.txt")
        
        # Tulis anotasi
        with open(annotation_file, 'w') as f:
            f.write(f"{class_id} {yolo_bbox[0]:.6f} {yolo_bbox[1]:.6f} {yolo_bbox[2]:.6f} {yolo_bbox[3]:.6f}\n")
        
        # Progress
        if (i + 1) % 100 == 0:
            print(f"Progress: {i + 1}/{len(image_files)} anotasi dibuat")
    
    print(f"\nSelesai! {len(image_files)} anotasi YOLO tersimpan di: {output_folder}")
    
    # Buat file classes.txt
    classes_file = os.path.join(output_folder, "classes.txt")
    with open(classes_file, 'w') as f:
        f.write("paru\n")  # Ganti dengan nama class yang sesuai
    
    print(f"File classes.txt dibuat: {classes_file}")
    print("\nCatatan: Edit classes.txt sesuai dengan nama class objek Anda")


def visualize_annotations(images_folder, annotations_folder, num_samples=None):
    """
    Visualisasi beberapa gambar dengan anotasi untuk verifikasi
    """
    image_files = sorted(glob.glob(os.path.join(images_folder, "*.jpg")))
    image_files += sorted(glob.glob(os.path.join(images_folder, "*.png")))
    
    if len(image_files) == 0:
        print("Tidak ada gambar untuk divisualisasi")
        return
    
    # Ambil sampel gambar atau semua gambar
    if num_samples is None:
        sample_files = image_files
        print(f"\nMemvisualisasi SEMUA {len(sample_files)} gambar...")
    else:
        import random
        sample_files = random.sample(image_files, min(num_samples, len(image_files)))
        print(f"\nMemvisualisasi {len(sample_files)} sampel...")
    
    print("Tekan tombol apapun untuk lanjut ke gambar berikutnya, 'q' untuk keluar")
    
    for image_file in sample_files:
        img = cv2.imread(image_file)
        if img is None:
            continue
        
        img_height, img_width = img.shape[:2]
        
        # Baca anotasi
        base_name = os.path.splitext(os.path.basename(image_file))[0]
        annotation_file = os.path.join(annotations_folder, f"{base_name}.txt")
        
        if not os.path.exists(annotation_file):
            print(f"Warning: Anotasi tidak ditemukan untuk {image_file}")
            continue
        
        with open(annotation_file, 'r') as f:
            for line in f:
                data = line.strip().split()
                class_id = int(data[0])
                center_x, center_y, width, height = map(float, data[1:])
                
                # Konversi ke pixel coordinates
                center_x *= img_width
                center_y *= img_height
                width *= img_width
                height *= img_height
                
                x1 = int(center_x - width / 2)
                y1 = int(center_y - height / 2)
                x2 = int(center_x + width / 2)
                y2 = int(center_y + height / 2)
                
                # Gambar rectangle
                cv2.rectangle(img, (x1, y1), (x2, y2), (0, 255, 0), 2)
                cv2.putText(img, f"Class {class_id}", (x1, y1-10), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 2)
        
        # Resize jika terlalu besar
        height, width = img.shape[:2]
        if height > 800:
            scale = 800 / height
            new_width = int(width * scale)
            new_height = 800
            img = cv2.resize(img, (new_width, new_height))
        
        cv2.imshow('Verifikasi Anotasi', img)
        key = cv2.waitKey(0) & 0xFF
        
        if key == ord('q'):
            break
    
    cv2.destroyAllWindows()
    print("Verifikasi selesai!")


if __name__ == "__main__":
    # ===== KONFIGURASI =====
    images_folder = "output_images_paru"  # Folder berisi gambar hasil ekstraksi video
    annotations_folder = "yolo_annotations_paru"  # Folder untuk menyimpan anotasi YOLO
    class_id = 1  # ID class untuk YOLO (0 = class pertama)
    
    print("="*60)
    print("PROGRAM PEMBUATAN ANOTASI YOLO OTOMATIS")
    print("="*60)
    
    # Buat anotasi
    create_yolo_annotations(images_folder, annotations_folder, class_id)
    
    # Visualisasi untuk verifikasi
    print("\n" + "="*60)
    response = input("Ingin melihat visualisasi hasil anotasi? (y/n): ")
    if response.lower() == 'y':
        visualize_annotations(images_folder, annotations_folder, num_samples=None)
    
    print("\n" + "="*60)
    print("SELESAI!")
    print("="*60)
    print(f"Gambar: {images_folder}")
    print(f"Anotasi: {annotations_folder}")
    print("\nStruktur folder untuk YOLO training:")
    print(f"  - {images_folder}/     <- gambar training")
    print(f"  - {annotations_folder}/  <- label file .txt")
    print(f"  - {annotations_folder}/classes.txt  <- daftar nama class")
