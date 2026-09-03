# Orbit Control untuk Linux

Aplikasi desktop native untuk memantau dan mengelola modem **Huawei/Orbit B312** melalui API HiLink lokal. Aplikasi dibuat dengan Kotlin dan Compose Desktop; tidak memakai browser, WebView, Electron, maupun layanan backend.

Versi saat ini: **v0.5.0-linux**.

## Fitur

- Login HiLink dengan cookie SessionID, verification token, retry, dan login ulang bila diperlukan.
- Dashboard status modem, operator, traffic, sinyal, dan jumlah perangkat.
- Band Lock manual satu band LTE, dengan konfirmasi serta pembacaan ulang modem untuk verifikasi.
- Statistik penggunaan dari counter modem dan diagnosis koneksi.
- Daftar perangkat, detail firmware, logout, serta Debug Report TXT.
- Simpan dan buka lokasi Debug Report melalui dialog desktop Linux.

Password modem tidak disimpan. Host dan username dapat disimpan secara opsional pada data privat pengguna di `~/.orbit-control`.

## Dukungan Linux

- Target utama: **Linux x64**.
- Paket **`.deb`** untuk Debian, Ubuntu, dan Linux Mint.
- Paket **`.rpm`** untuk Fedora, RHEL, serta openSUSE.
- Paket hasil build membawa Java runtime yang dibutuhkan, sehingga pengguna akhir tidak perlu memasang Java.
- Pembuatan paket harus dijalankan pada mesin Linux. Gunakan JDK 17 untuk build.

## Membuat paket

1. Instal JDK 17 dan pastikan `java -version` berjalan.
2. Ekstrak source ini di komputer Linux.
3. Buka Terminal pada folder project.
4. Pastikan alat pembuat paket tersedia: dpkg-deb untuk paket .deb, atau rpmbuild (umumnya berasal dari paket rpm-build) untuk paket .rpm.
5. Jalankan:

```bash
bash build-linux.sh
```

Skrip akan membuat format paket yang didukung oleh sistem build Anda. Untuk memilih format secara langsung:

```bash
bash ./gradlew packageDeb
# atau
bash ./gradlew packageRpm
```

Paket akan tersedia di:

```text
build/compose/binaries/main/
```

## Instalasi

Untuk Ubuntu/Debian/Mint:

```bash
sudo apt install ./orbit-control_0.5.0_amd64.deb
```

Untuk Fedora/openSUSE/RHEL, gunakan paket `.rpm` yang dihasilkan melalui aplikasi Software Center atau manajer paket distribusi Anda.

## Pemakaian

1. Sambungkan komputer Linux ke Wi-Fi/LAN modem B312.
2. Jalankan **Orbit Control** dari menu aplikasi.
3. Masukkan host modem—bawaan `http://192.168.8.1`—username, dan password.
4. Gunakan **Test Koneksi**, lalu tekan **Masuk**.
5. Buka Ringkasan, Tools, Perangkat, atau Setelan sesuai kebutuhan.

Band Lock bersifat eksperimental. Aplikasi hanya menerapkan perubahan setelah Anda memilih satu band dan menyetujui konfirmasi; hasil kemudian diverifikasi melalui pembacaan ulang modem.

## Batasan

- Dukungan endpoint dan nama field XML berbeda antar firmware Huawei/Orbit B312.
- Band Lock dapat menjadi read-only jika endpoint atau konfigurasi firmware tidak dapat diverifikasi.
- Dialog simpan dan buka folder bergantung pada desktop environment Linux yang digunakan; uji pada GNOME atau KDE sebelum rilis luas.
- Fitur riset—Log Radio/CSV, rekomendasi band, throughput/Speed Test, dan Antenna A/B—tidak disertakan.
