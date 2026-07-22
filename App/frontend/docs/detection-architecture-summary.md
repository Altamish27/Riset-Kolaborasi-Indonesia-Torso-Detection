# Ringkasan Arsitektur Deteksi Organ (Frontend v2.0)

## Tujuan
Menjelaskan alur antara preview kamera, deteksi lokal, dan klasifikasi akhir di backend Gemini Vision.

## Komponen utama

1. `CameraPreview.kt`
   - Menyediakan live camera preview menggunakan CameraX PreviewView.
   - Mengikat `Preview` dan `ImageAnalysis` ke lifecycle aplikasi.
   - Memproses frame kamera secara terus-menerus.

2. `TFLiteObjectAnalyzer.kt`
   - Analyzer lokal berbasis model TFLite/YOLO.
   - Mendeteksi kandidat organ secara real-time dari frame preview.
   - Menghasilkan `DetectionResult` untuk overlay UI dan sebagai trigger snapshot.
   - Menyediakan `requestCapture(callback: (ByteArray) -> Unit)` untuk mengambil satu frame JPEG saat diminta.

3. `ScanAnatomyScreen.kt`
   - Mengontrol alur pengguna di layar scan anatomi.
   - Menampilkan status, threshold deteksi, hasil deteksi lokal, tombol shutter, dan loading indicator.
   - Mengatur auto-capture dengan:
     - deteksi stabil selama `DETECTION_STABILITY_MS`
     - cooldown antar capture `AUTO_CAPTURE_COOLDOWN_MS`
   - Mengirim snapshot ke backend jika kondisi stabil terpenuhi.
   - Menangani TTS (`AudioAssistant`) dan haptic (`HapticHelper`).

4. `DetectionRepository.kt`
   - Mengenkapsulasi panggilan jaringan ke endpoint backend `/api/detect`.
   - Mengembalikan hasil terstruktur (`Success`/`Failure`).

5. `ApiService.kt`
   - Retrofit API interface.
   - Mendefinisikan endpoint `@Multipart @POST("/api/detect")`.

6. `ApiModels.kt`
   - Model serialisasi untuk respons backend:
     - `DetectionApiResponse(status, class_id, class_name, confidence, description)`.

## Alur kerja versi 2.0

1. User membuka layar `ScanAnatomyScreen` dan CameraX mulai menampilkan preview.
2. `TFLiteObjectAnalyzer` menganalisis frame secara terus-menerus dan menampilkan kandidat deteksi lokal.
3. Jika `currentDetection` muncul dan `isLearningActive == true`, aplikasi menunggu deteksi stabil selama minimal `DETECTION_STABILITY_MS` (misalnya 900 ms).
4. Setelah stabil dan cooldown sejak capture sebelumnya terpenuhi (`AUTO_CAPTURE_COOLDOWN_MS`, misalnya 3500 ms), aplikasi memicu `requestCapture()` pada analyzer.
5. Analyzer mengompres frame saat ini menjadi JPEG bytes dan memanggil callback.
6. UI menvalidasi ukuran bytes (< 10 MB).
   - Jika terlalu besar, proses dibatalkan dan pengguna mendapat TTS + long haptic.
7. Jika valid, snapshot dikirim ke `DetectionRepository.detectImageBytes(context, bytes)`.
8. `DetectionRepository` memanggil backend API `POST /api/detect`.
9. Backend Gemini Vision mengembalikan klasifikasi organ:
   - `status = "detected" | "not_detected"`
   - `class_name`, `confidence`, `description`
10. UI menampilkan hasil dan memanggil `AudioAssistant.speak(...)`:
    - Jika `detected`: "Model organ terdeteksi: <class_name>."
    - Jika `not_detected`: membacakan `description` dari backend.
11. Jika berhasil, organ dapat dikunci dalam status `lockedOrgan`.

## Keuntungan desain v2.0

- Menggunakan model lokal sebagai trigger sehingga backend tidak dipanggil terus-menerus.
- Memberi umpan balik cepat dan aksesibilitas untuk tunanetra.
- Memisahkan concerns:
  - lokal = deteksi kandidat dan snapshot
  - backend = klasifikasi akhir dan validasi organ

## Parameter stabilitas

- `DETECTION_STABILITY_MS` (900 ms): memastikan capture hanya terjadi jika deteksi lokal bertahan cukup lama.
- `AUTO_CAPTURE_COOLDOWN_MS` (3500 ms): mencegah multiple capture beruntun dan mengurangi beban backend.

## Catatan teknis

- YOLO/TFLite lokal masih digunakan sebagai trigger; belum sepenuhnya diganti oleh backend.
- Backend Gemini dipakai untuk klasifikasi akhir dan output teks penjelasan.
- Jika diperlukan, mode backend-only dapat ditambahkan kemudian untuk mengirim snapshot berkala tanpa trigger lokal.
