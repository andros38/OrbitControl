# Orbit Control Android

Orbit Control adalah aplikasi Android native untuk mengelola modem Huawei/Orbit B312 melalui API HiLink lokal. Aplikasi menggunakan Kotlin, Jetpack Compose, OkHttp, parser XML, dan pola MVVM; tidak memakai WebView atau layanan backend.

Versi saat ini: **v0.5.0**.

## Fungsi yang tersedia

- Login modem dengan SessionID/cookie dan verification token Huawei.
- Dashboard status modem, operator, PLMN, traffic, WAN/LAN, perangkat, dan metrik sinyal.
- Band Lock manual satu band LTE, dengan konfirmasi pengguna dan pembacaan ulang untuk verifikasi.
- Statistik pemakaian dari counter modem.
- Diagnosis koneksi berbasis HTTP latency.
- Daftar perangkat yang terhubung.
- Detail teknis firmware, logout, dan Export Debug Report melalui Android share sheet.

Fitur riset—rekomendasi band, uji throughput/Speed Test, pembandingan Antenna A/B, serta Log Radio dan ekspor CSV—telah dihapus dari aplikasi.

## Build

Prasyarat: JDK 17, Android SDK Platform 35, Android SDK Build Tools 35.x, serta ANDROID_HOME atau ANDROID_SDK_ROOT yang menunjuk ke Android SDK.

Dari folder project jalankan: gradlew.bat assembleDebug

APK debug dihasilkan pada app\\build\\outputs\\apk\\debug\\app-debug.apk.

## Pemakaian

1. Sambungkan ponsel ke Wi-Fi/LAN modem B312.
2. Buka Orbit Control dan masukkan host modem (default http://192.168.8.1), username, serta password.
3. Gunakan Test Koneksi bila perlu, lalu tekan Masuk.
4. Buka Ringkasan, Tools, Perangkat, atau Setelan sesuai kebutuhan.

Password tidak disimpan. Opsi simpan konfigurasi hanya menyimpan host dan username. HTTP cleartext diizinkan karena WebUI B312 umumnya tersedia lewat HTTP lokal; gunakan aplikasi pada LAN tepercaya.

## Band Lock

Band lock hanya bekerja bila endpoint dan konfigurasi firmware dapat diverifikasi. Aplikasi tidak menjalankan pemilihan band otomatis. Setelah pengguna memilih satu band dan mengonfirmasi, aplikasi mengirim perubahan lalu membaca ulang modem untuk memastikan hasilnya. Jika verifikasi gagal, aplikasi mencoba memulihkan konfigurasi sebelumnya.

## Batasan firmware B312

- Dukungan endpoint, nama field XML, dan informasi sinyal berbeda antar firmware/operator.
- Kode 100002 menandakan endpoint tidak didukung; 100003 menandakan sesi atau hak akses bermasalah.
- Firmware dapat membatasi satu sesi WebUI aktif.
- Statistik bulanan, daftar perangkat, atau metrik sinyal dapat kosong meski modem aktif.

Debug Report tidak memuat password, nilai SessionID/token, IMEI, serial number, ataupun XML mentah.
