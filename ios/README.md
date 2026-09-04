# Orbit Control iOS

Versi native iOS 15+ untuk manajemen modem Huawei/Orbit B312. Fokus aplikasi ini adalah manajemen modem, bukan fitur penelitian.

Fitur yang tersedia:

- login ke modem HiLink melalui HTTP/HTTPS lokal;
- dashboard status, sinyal radio, lalu lintas, dan jaringan;
- daftar perangkat Wi-Fi/LAN;
- Band Lock manual untuk satu band LTE dengan konfirmasi, pembacaan ulang, dan upaya rollback;
- statistik pemakaian;
- diagnosis koneksi HTTP/HTTPS ringan;
- detail modem/firmware dan laporan debug yang menyembunyikan password, token, SessionID, IMEI, serial, serta XML mentah.

## Prasyarat

- Mac dengan Xcode 15 atau yang lebih baru;
- Apple ID untuk menjalankan di perangkat fisik, atau Simulator iOS 15+;
- iPhone/iPad terhubung ke Wi-Fi/LAN modem.

## Membuka dan menjalankan

1. Buka `OrbitControliOS.xcodeproj` di Xcode.
2. Pada target **OrbitControliOS**, buka **Signing & Capabilities**, lalu pilih Team Apple Anda.
3. Ubah **Bundle Identifier** bila identifier bawaan sudah digunakan.
4. Pilih perangkat atau Simulator iOS 15+.
5. Tekan **Run**.
6. Saat pertama kali aplikasi meminta izin jaringan lokal, pilih **Izinkan**.
7. Masuk menggunakan host modem, misalnya `http://192.168.8.1`.

## Catatan jaringan

Konfigurasi ATS menggunakan `NSAllowsLocalNetworking`; aplikasi tidak mengaktifkan `NSAllowsArbitraryLoads`. Jika firmware modem hanya melayani HTTP pada alamat lokal, iOS tetap akan menampilkan permintaan izin jaringan lokal saat aplikasi mengakses modem.

## Catatan Band Lock

Implementasi memakai endpoint kandidat `/api/net/net-mode` yang umum pada HiLink B312. Kemampuan write endpoint dan daftar band berbeda menurut firmware/operator. Tombol penerapan hanya aktif bila konfigurasi yang diperlukan berhasil dibaca. Selalu catat band sebelumnya dan siapkan akses ke antarmuka web modem untuk pemulihan manual.

## Distribusi

Untuk memasang ke perangkat sendiri, jalankan dari Xcode dengan signing Apple ID. Untuk TestFlight/App Store, gunakan akun Apple Developer, isi App Icon, metadata privasi, dan archive melalui menu **Product → Archive**.
