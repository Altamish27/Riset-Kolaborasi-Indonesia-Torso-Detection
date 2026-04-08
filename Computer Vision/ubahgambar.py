import cv2
import os

def video_to_images(video_path, output_folder, frame_skip=1):
    """
    Mengubah video menjadi gambar-gambar
    
    Parameters:
    - video_path: Path ke file video
    - output_folder: Folder untuk menyimpan gambar
    - frame_skip: Ambil setiap n frame (1 = semua frame, 2 = setiap 2 frame, dst)
    """
    
    # Buat folder output jika belum ada
    if not os.path.exists(output_folder):
        os.makedirs(output_folder)
    
    # Buka video
    video = cv2.VideoCapture(video_path)
    
    if not video.isOpened():
        print("Error: Tidak bisa membuka video!")
        return
    
    # Dapatkan informasi video
    total_frames = int(video.get(cv2.CAP_PROP_FRAME_COUNT))
    fps = int(video.get(cv2.CAP_PROP_FPS))
    width = int(video.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(video.get(cv2.CAP_PROP_FRAME_HEIGHT))
    
    print(f"Informasi Video:")
    print(f"Total Frame: {total_frames}")
    print(f"FPS: {fps}")
    print(f"Resolusi: {width}x{height}")
    print(f"\nMengekstrak frame...")
    
    frame_count = 0
    saved_count = 0
    
    while True:
        # Baca frame
        success, frame = video.read()
        
        if not success:
            break
        
        # Simpan frame sesuai frame_skip
        if frame_count % frame_skip == 0:
            output_path = os.path.join(output_folder, f"frame_{saved_count:06d}.jpg")
            cv2.imwrite(output_path, frame)
            saved_count += 1
            
            # Tampilkan progress setiap 100 frame
            if saved_count % 100 == 0:
                print(f"Progress: {saved_count} gambar tersimpan...")
        
        frame_count += 1
    
    # Tutup video
    video.release()
    
    print(f"\nSelesai!")
    print(f"Total {saved_count} gambar tersimpan di folder: {output_folder}")


if __name__ == "__main__":
    # ===== KONFIGURASI =====
    # Ganti dengan path video Anda
    video_path = "IMG_5102.MOV"
    
    # Folder output untuk menyimpan gambar
    output_folder = "output_images_paru"
    
    # Frame skip: 1 = ambil semua frame, 2 = setiap 2 frame, dst
    # Jika video 30 FPS dan frame_skip=30, maka 1 gambar per detik
    frame_skip = 1
    
    # Jalankan konversi
    video_to_images(video_path, output_folder, frame_skip)
