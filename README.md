# FRANZZ Orbit Android — client source lengkap

Source Android untuk pendamping FRANZZ Orbit. Build ini diselaraskan dengan backend web: URL awal sama dengan web, endpoint API sama, dan popup robot berisi seluruh alat utama.

## Fitur di popup robot

- **Scan & Fill** — membaca field aksesibilitas yang didukung, meminta saran ke server, menampilkan pratinjau, lalu menerapkan pilihan setelah pengguna menekan tombol.
- **Analisis Halaman AI** — menganalisis teks yang diekspos aplikasi aktif.
- **Fitur AI** — Tutor AI, Tanya AI, Penjelasan lengkap, Ringkas Materi, Cek Jawaban, dan Latihan Soal.
- **Chat AI** — chat beberapa giliran; ada input suara, pembacaan jawaban, dan pilihan untuk menyertakan konteks layar.
- Tidak memakai emoji untuk ikon menu; popup beranimasi dan input Chat AI dibuat lebih lebar/tinggi.

## Koneksi web/API

Default server: `https://www.franzzorbit.my.id` (dapat diganti di layar login).

- `GET /api/status`
- `POST /api/login`
- `POST /api/chat` (`mode`: `tutor`, `explain`, `summary`)
- `POST /api/analyze`
- `POST /api/gemini` (Scan & Fill)
- `POST /api/study` (`check`, `quiz`, `grade`)

APK tidak menyimpan Gemini API key atau kredensial Firebase. Keduanya berada di server. Token sesi disimpan pada `SharedPreferences` privat aplikasi; itu tidak sama dengan Android Keystore. Semua request jaringan diwajibkan memakai HTTPS.

## Build APK

**Lingkungan yang dibutuhkan:** JDK 17, Android SDK, platform Android 35, build-tools 35.0.0. Gradle 8.9, Android Gradle Plugin 8.7.2, dan Kotlin Gradle Plugin 2.1.20 dipilih dari rentang kompatibilitas resmi.

### Windows

```powershell
.\gradlew.bat --no-daemon clean assembleDebug
```

### Linux/macOS

```sh
./gradlew --no-daemon clean assembleDebug
```

Script `gradlew` / `gradlew.bat` di arsip ini adalah bootstrap ringan: mengunduh Gradle 8.9 pertama kali, memeriksa SHA-256 distribution, lalu meneruskan semua argumen ke Gradle. Folder `.tools` tidak perlu dimasukkan ke Git.

Hasil build debug:

`app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions

Workflow di `.github/workflows/build-apk.yml` membangun APK pada push ke `main`, pull request, atau `workflow_dispatch`. Setelah workflow sukses, unduh artifact **franzz-orbit-debug-apk** dari halaman Actions. Workflow telah diubah agar memakai Gradle 8.9 yang cocok dengan plugin build; workflow lama memakai Gradle 8.11.1 walau Gradle wrapper proyek 8.9 dan plugin lama masih ketinggalan.

### Android Studio

Buka folder proyek ini dari Android Studio, gunakan JDK 17 untuk Gradle, dan izinkan Android Studio memasang Android SDK platform 35/build-tools 35.0.0 jika diminta. Setelah Sync selesai, pilih `app` lalu Build APK.

## Audit client/backend

- Referensi ikon manifest memakai `@mipmap/ic_launcher`; resource launcher yang dipakai tersedia di `app/src/main/res`.
- Server awal tidak lagi kosong.
- Popup menampilkan Scan & Fill, Analisis Halaman AI, enam alat Fitur AI, dan Chat AI.
- Pemeriksaan status tidak melaporkan koneksi hijau bila panel Firestore server mengembalikan `panel: false`.
- Jika berganti dari Tutor AI ke Tanya AI, percakapan lokal sebelumnya dibersihkan agar instruksi mode tidak tercampur.
- Saat menerapkan Scan & Fill, pilihan checkbox disinkronkan dengan jawaban yang dipilih, termasuk menghapus centang lama yang tidak dipilih.
- Kolom password dikecualikan dari aksesibilitas. Teks layar hanya dikirim saat pengguna menjalankan fitur layar atau mengaktifkan konteks layar pada chat.

### Batasan yang perlu diketahui

- Android membaca accessibility tree, bukan DOM browser atau screenshot. Aplikasi yang tidak mengekspos teks/field ke Accessibility API mungkin tidak terbaca dengan baik; aplikasi custom widget dan dropdown tidak standar dapat memerlukan input manual. Ini berbeda dari client web desktop yang dapat membaca HTML halaman yang didukung.
- Uji lokal pada paket ini mencakup pemeriksaan kontrak statis. Build Android penuh perlu dilakukan di mesin/CI dengan Android SDK; environment pembuatan ZIP ini tidak memiliki Gradle dan Android SDK. Jangan menganggap APK sudah berhasil dibuat hanya dari lulusnya pemeriksaan statis.
- Endpoint status memeriksa konfigurasi dan koneksi baca Firestore, bukan jaminan izin tulis. Verifikasi login pengguna, pembuatan akun, serta pengaturan kuota pada deployment staging.
