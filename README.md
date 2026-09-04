# Orbit Control

Orbit Control adalah aplikasi manajemen modem untuk Huawei/Orbit B312 yang berkomunikasi langsung melalui Huawei HiLink API pada jaringan lokal.

Aplikasi tersedia atau sedang disiapkan untuk Android, Windows, Linux, dan iOS. Fokus utamanya adalah pemantauan serta pengelolaan modem, termasuk Band Lock LTE manual.

## Screenshot

### Dashboard / Ringkasan

<p align="center">
  <img src="screenshot/img1.png" alt="Orbit Control Dashboard" width="500">
</p>

### Band Lock

<p align="center">
  <img src="screenshot/img2.png" alt="Orbit Control Band Lock" width="500">
</p>

### Statistik

<p align="center">
  <img src="screenshot/img3.png" alt="Orbit Control Statistics" width="500">
</p>

## Download dan status platform

Jika Anda hanya ingin menggunakan Orbit Control, unduh aplikasi siap pakai melalui halaman **Releases**.

| Platform | Status v0.5.0 | Distribusi |
| --- | --- | --- |
| Android | Tersedia | APK |
| Windows | Tersedia | Installer `.exe` dan `.msi` |
| Linux | Source tersedia | Build mandiri menjadi `.deb` atau `.rpm` |
| iOS/iPadOS | Source tersedia | Build melalui Xcode di macOS |

### Android

- `Orbit-Control-0.5.0-Android.apk`

### Windows

- `Orbit.Control-0.5.0-Windows-Setup.exe` — direkomendasikan
- `Orbit.Control-0.5.0-Windows.msi`

### Linux

Binary Linux belum tersedia pada v0.5.0. Source code Linux tersedia pada folder `linux/`.

### iOS dan iPadOS

Versi native iOS/iPadOS telah disiapkan untuk **iOS 15+** menggunakan SwiftUI. Source code tersedia pada folder `ios/`.

IPA siap instal belum didistribusikan secara publik karena aplikasi iOS harus dibangun dan ditandatangani menggunakan Xcode serta akun Apple Developer. Pengguna dapat menjalankan proyek dari Xcode untuk perangkat pribadi atau Simulator.

> Android membutuhkan minimal Android 7.0 (API 24). Untuk Windows, target rilis utama adalah Windows 10/11 x64. Linux ditargetkan untuk Linux x64. Versi iOS mendukung iPhone dan iPad dengan iOS/iPadOS 15 atau lebih baru.

## Fitur utama

- Login ke modem melalui Huawei HiLink API.
- Dashboard status modem, operator, traffic, WAN/LAN, perangkat, dan metrik sinyal.
- **Band Lock LTE manual** dengan konfirmasi dan verifikasi konfigurasi setelah perubahan.
- Statistik penggunaan dari counter modem.
- Diagnosis koneksi berbasis HTTP latency.
- Daftar perangkat yang terhubung.
- Informasi firmware dan status teknis modem.
- Export Debug Report tanpa menyertakan password, SessionID/token, IMEI, atau serial number.

## Cara menggunakan

1. Sambungkan perangkat ke Wi-Fi/LAN modem Huawei/Orbit B312.
2. Jalankan Orbit Control.
3. Masukkan alamat modem. Default yang umum digunakan adalah `http://192.168.8.1`.
4. Masukkan username dan password modem.
5. Gunakan **Test Koneksi** bila diperlukan, kemudian pilih **Masuk**.
6. Gunakan menu **Ringkasan**, **Tools**, **Perangkat**, dan **Setelan** untuk mengelola modem.

Pada iOS/iPadOS, izinkan akses **Local Network** ketika sistem meminta izin agar aplikasi dapat menghubungi modem pada jaringan lokal.

Password modem tidak disimpan. Bila opsi penyimpanan konfigurasi digunakan, aplikasi hanya menyimpan host dan username.

## Source code

Repository ini menggunakan struktur berikut:

```text
OrbitControl/
├── android/     # Android — Kotlin + Jetpack Compose
├── windows/     # Windows — Kotlin + Compose Desktop
├── linux/       # Linux — Kotlin + Compose Desktop
├── ios/         # iOS/iPadOS — Swift + SwiftUI
├── screenshot/  # Dokumentasi tampilan aplikasi
├── CHANGELOG.md
└── README.md
