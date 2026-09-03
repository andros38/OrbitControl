# Orbit Control untuk Windows

Versi desktop native untuk memantau dan mengelola modem **Huawei/Orbit B312** melalui API HiLink di jaringan lokal. Aplikasi ini memakai Kotlin dan Compose Desktop; tidak menggunakan browser, WebView, Electron, maupun layanan backend.

## Status port

Basisnya adalah `Orbit Control Android v0.4.0-personal`. Versi Windows ini difokuskan pada pengelolaan modem: API Huawei HiLink, autentikasi sesi/token, parser XML, pemantauan status, statistik, diagnosis, perangkat, serta Band Lock manual. Lapisan Android diganti dengan implementasi Windows.

| Fitur | Versi Windows |
|---|---|
| Login HiLink, cookie SessionID, verification token, retry/re-login | Ya |
| Dashboard status, operator, traffic, sinyal, dan perangkat | Ya |
| Band Lock manual + verifikasi pembacaan ulang modem | Ya |
| Statistik penggunaan dan diagnosis koneksi | Ya |
| Detail firmware, logout, dan Debug Report | Ya |
| Debug report TXT | Ya |
| Android share sheet / Storage Access Framework | Diganti dialog **Simpan** Windows dan buka folder berkas |

Password modem tidak disimpan. Host dan username boleh disimpan secara opsional di data privat Windows pada `%USERPROFILE%\.orbit-control`.

## Dukungan Windows

- **Windows 10/11 x64** adalah target rilis.
- Installer yang dihasilkan adalah `.exe` dan `.msi`, serta membawa Java runtime sendiri sehingga pengguna akhir tidak perlu memasang Java.
- Windows **x86/32-bit tidak disertakan**. Rantai rilis Compose Desktop yang digunakan mendukung paket Windows `x64` dan `arm64`, bukan `x86`; memaksakannya akan membuat build tidak didukung. Jika perangkat Anda 32-bit, tetap gunakan aplikasi Android.

## Membuat installer di Windows

1. Instal JDK 17 x64 dan pastikan perintah `java -version` berjalan di Command Prompt.
2. Unduh source ini lalu ekstrak.
3. Klik dua kali `build-windows.bat`, atau jalankan perintah berikut dari Command Prompt:

```bat
gradlew.bat packageDistributionForCurrentOS
```

4. Setelah selesai, installer tersedia dalam `build\compose\binaries\main`.
5. Jalankan installer `.exe` atau `.msi`, lalu buka **Orbit Control** dari Start Menu.

Build pertama membutuhkan koneksi internet untuk mengambil dependensi Gradle. Pembuatan installer harus dilakukan dari Windows; ini memang batas paket Compose Desktop.

## Pemakaian

1. Sambungkan komputer ke Wi-Fi/LAN modem B312.
2. Jalankan Orbit Control.
3. Isi host modem (standarnya `http://192.168.8.1`), username, dan password modem.
4. Gunakan **Test Koneksi**, lalu **Masuk**.
5. Gunakan menu kiri untuk membuka Ringkasan, Tools, Perangkat, dan Setelan.

Band lock tetap bersifat eksperimental: aplikasi hanya mengirim perubahan setelah konfirmasi pengguna, kemudian membaca ulang konfigurasi untuk verifikasi. Ketersediaannya bergantung pada firmware B312.

## Pemeriksaan sebelum digunakan rutin

- Pastikan Dashboard menampilkan sinyal dan operator yang sesuai dengan halaman WebUI modem.
- Uji Band Lock hanya dengan satu band yang Anda pahami, lalu pastikan modem kembali terhubung.
- Buat Debug Report dan pastikan password, nilai token, SessionID, IMEI, serta serial number tidak muncul.

Jangan kirimkan password modem kepada siapa pun saat melakukan pengujian atau pelaporan masalah.
