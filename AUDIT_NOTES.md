# Catatan audit FRANZZ Orbit Android

Tanggal: 11 Oktober 2026.

## Yang terlihat dari workflow repo asli

GitHub Actions terbaru yang dapat dibaca secara publik menunjukkan run `Build APK #10` gagal di `gradle assembleDebug --no-daemon`. Log detail meminta sesi GitHub, sehingga pesan compiler yang persis tidak dapat diverifikasi dari tampilan publik. Konfigurasi yang bisa diperiksa menunjukkan workflow meng-install Gradle 8.11.1, sedangkan proyek memiliki Gradle Wrapper 8.9 dan plugin Android/Kotlin lama. Ini ketidakkonsistenan build yang dikoreksi di source ini.

## Perubahan source

- Android Gradle Plugin 8.7.2 + Kotlin Gradle Plugin 2.1.20 + Gradle 8.9.
- CI dibangun menggunakan Gradle 8.9 serta Android platform 35 dan Java 17.
- Referensi launcher icon diperbaiki menjadi resource mipmap yang tersedia.
- Default server Android disamakan dengan host web.
- Popup menu Android dilengkapi Scan & Fill, Analisis Halaman AI, enam item Fitur AI, serta Chat AI.
- Pemeriksaan koneksi menampilkan Firestore tidak siap sebagai status gagal, bukan hijau.
- Kontrak endpoint/request-response didokumentasikan dan dicek secara statis.

## Batas verifikasi

Source website diuji lewat `npm test` dan seluruh suite lolos. Tes backend Firebase memakai fake/mock Firestore, bukan kredensial produksi. Environment di sesi pembuatan arsip tidak mempunyai Gradle atau Android SDK dan tidak bisa menjangkau host distribution dari container, jadi APK belum dibuild di lingkungan ini. Gunakan GitHub Actions/Android Studio untuk verifikasi kompilasi sebenarnya sebelum rilis.
