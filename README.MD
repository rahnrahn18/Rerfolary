# Folar

A modern, Android-first camera library for Compose Multiplatform.
Forked from **CameraK** by **Kashif Mehmood**.

## Features

- 📱 **Android Only**: Optimized for high-performance Android camera experiences (arm64-v8a).
- 📸 **Compose-First**: Native Compose API.
- 🎯 **Flexible Configuration**: Aspect ratios, zoom, flash control.
- 🔌 **Plugin System**: Modular QR/barcode scanning, OCR, image saving.
- ⚡ **Optimized Capture**: Direct file saving.

# Folar: Professional Photography Reimagined

> **Folar** bukan sekadar kamera; ini adalah kanvas digital Anda. Menggabungkan kontrol manual tingkat profesional dengan antarmuka minimalis berbasis gestur yang modern.

## 🚀 Tentang Proyek
**Folar** (sebelumnya dikenal sebagai proyek pemindai utilitas) telah berevolusi total. Kami meninggalkan arsitektur *legacy* yang kaku demi pengalaman fotografi *mobile* yang *seamless*. Fokus utama kami adalah **Fotografi Murni** dengan kemampuan *computational photography* yang kuat, di mana fitur pemindai (OCR/QR) kini menjadi fitur pendukung yang terintegrasi secara halus, bukan fitur utama.

## ✨ Fitur Utama (Core Features)

### 📸 Professional Imaging
* **Manual Controls (Pro Mode):** Kontrol penuh atas ISO, Shutter Speed, Focus Peaking, dan White Balance dengan slider haptic yang presisi.
* **Aperture & Depth Control:** Simulasi kedalaman bidang (Depth of Field) real-time yang dapat diatur.
* **Raw & Format Support:** Dukungan native untuk RAW (DNG), JPEG, dan HEIC.
* **Smart HDR & Low Light:** Pemrosesan gambar tingkat lanjut untuk rentang dinamis tinggi.

### 🎨 Modern UI/UX
* **Gesture-Driven Interface:** Tidak ada lagi tombol kaku. Geser (swipe) untuk ganti mode, cubit (pinch) untuk zoom, ketuk & tahan untuk fokus.
* **Fluid Animations:** Transisi antar-menu yang halus, animasi *shutter* yang responsif, dan elemen UI yang *floating* (melayang) di atas viewfinder.
* **Minimalist HUD:** Tampilan layar bersih (distraction-free). Parameter hanya muncul saat dibutuhkan.

### 🧩 Integrated Intelligence (Ex-Plugins)
* Fitur pintar kini tertanam dalam "Smart Mode", bukan modul terpisah:
    * **Live Text (OCR):** Deteksi teks instan langsung dari viewfinder tanpa memotret.
    * **Smart Scan (QR/Barcode):** Pengenalan kode otomatis yang tidak mengganggu framing foto.

## 🛠 Arsitektur & Tech Stack
### 🧠 Computational Photography Engine
Folar menggunakan pendekatan *Hybrid-Processing* untuk memanipulasi gambar:

* **AI-Powered Aperture (Bokeh Engine):**
    * Didukung oleh **BokehDepthOfField (by Erfan-Ahmadi)** berbasis TensorFlow Lite.
    * Memungkinkan efek *Depth of Field* (bokeh) yang realistis bahkan pada perangkat **Single Camera** (tanpa hardware depth sensor).
    * **Fitur:** Segmentasi manusia presisi tinggi dengan *edge detection* yang halus.

* **Color Science (GPUPixel):**
    * Pemrosesan warna real-time berbasis GPU (OpenGL/Vulkan) untuk filter sinematik dan koreksi warna (LUTs).

* **Hardware Adaptation:**
    * Sistem secara otomatis mendeteksi ketersediaan lensa (Wide/Tele).
    * Pada perangkat lensa tunggal, sistem memaksimalkan *Software Bokeh* untuk kompensasi kekurangan hardware.

### 🛠 Tech Stack Dependencies
* **Camera:** CameraX / Camera2 API
* **ML Engine:** TensorFlow Lite (via BokehDepthOfField lib)
* **Filter Engine:** GPUPixel
* **Architecture:** MVVM Clean Architecture


---
*Folar Project - Dibuat dengan presisi untuk fotografer mobile.*
