# FRANZZ Orbit (v0.1.0)

Aplikasi Android: browser dalam aplikasi + robot melayang yang membaca form, meminta jawaban ke Gemini, lalu mengisinya.
Tidak pernah menekan Lanjut / Kirim / Selesai. Kamu yang menekannya.

## Cara mendapatkan APK (tanpa Android Studio)
1. Buat repository baru di GitHub, unggah isi folder ini (termasuk folder `.github`).
2. Buka tab **Actions** > **Build APK** > **Run workflow** (atau cukup push ke branch `main`).
3. Setelah hijau, unduh artifact **franzz-orbit-debug-apk** > ekstrak > pasang `app-debug.apk`.

## Pemakaian
1. Buka ⚙, isi API key Gemini, cek nama model, tekan **Simpan & tes**.
2. Tempel link form lalu tekan **Isi otomatis**. Untuk halaman berikutnya, tekan Lanjut sendiri lalu ketuk robot > **Scan & Fill**.

## Status
Belum pernah dikompilasi atau diuji di perangkat. Nama model default belum diverifikasi.
