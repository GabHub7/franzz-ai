# Franzz Orbit untuk Android (Bot Franzz melayang)

Robot Franzz tampil melayang di atas **web atau aplikasi apa pun** (Chrome, aplikasi sekolah, dll). Ada tombol on/off di aplikasi,
tanpa tempel link web. Ketuk robot untuk chat dengan AI tentang layar yang sedang terbuka, atau tekan **Isi kolom di layar**:
AI menjawab dulu, kamu lihat, lalu **Terapkan ke layar** mengisi kolomnya. Tombol Kirim di situs/aplikasi itu tetap kamu tekan sendiri.

Perlu backend Orbit versi Vercel (`franzz-orbit-vercel.zip`) yang sudah punya `/api/chat` dan `/api/gemini`.

## Cara mendapat APK
**A. GitHub Actions (tanpa Android Studio)**
1. Buat repo baru (private) dari folder ini lalu push ke branch `main`.
2. Opsional, supaya alamat server terisi otomatis di aplikasi: repo > Settings > Secrets and variables > Actions > **Variables**
   > New repository variable: `ORBIT_SERVER` = `https://nama-project.vercel.app`.
3. Tab **Actions** > "Build APK" (jalan otomatis saat push, atau Run workflow) > unduh artifact **franzz-orbit-apk** > `app-debug.apk`.

**B. Android Studio**: buka folder ini, tunggu sync, lalu Build > Build APK(s). Alamat server default bisa diisi di `gradle.properties` (`ORBIT_SERVER=`).

APK yang dihasilkan bertanda tangan debug: aman untuk dipasang sendiri (sideload), bukan untuk Play Store.

## Pemakaian pertama
1. Pasang APK, buka **Franzz Orbit**, masuk dengan username dan password admin Orbit (alamat server sudah terisi bila `ORBIT_SERVER` diatur).
2. Nyalakan **Bot Franzz aktif**. Android membuka pengaturan aksesibilitas: nyalakan layanan **Bot Franzz**.
   Kalau Android menolak ("Pengaturan terbatas", normal untuk aplikasi di luar Play Store): Pengaturan > Aplikasi > Franzz Orbit >
   titik tiga di kanan atas > **Izinkan pengaturan terbatas**, lalu ulangi.
3. Robot muncul. Geser untuk memindahkan, ketuk untuk membuka panel. Matikan kapan saja lewat tombol yang sama di aplikasi.
4. Isi **Tentang saya** di aplikasi supaya jawaban "Isi kolom" sesuai profilmu.

## Cara kerja dan privasi
- Layanan aksesibilitas membaca teks layar aplikasi yang terbuka dan menampilkan robot sebagai overlay (tidak perlu izin "tampil di atas aplikasi lain").
- Layar hanya dikirim ke servermu saat kamu mengirim pesan dengan centang **Sertakan layar**, atau menekan **Isi kolom**. Tidak ada pengiriman otomatis.
- Kolom password tidak pernah dibaca. Token login disimpan di penyimpanan privat aplikasi. Hanya koneksi `https://`.
- Chat dihitung sebagai kuota Gemini milikmu (lihat batas 429 di README web).

## Batasan
- Kolom yang bisa diisi: teks, pilihan tunggal (radio), pilihan ganda (checkbox) yang **terlihat di layar**. Gulir dulu lalu tekan lagi untuk bagian bawah. Dropdown tidak didukung.
- Beberapa aplikasi menyembunyikan isinya dari aksesibilitas (mis. aplikasi perbankan atau jendela aman); botnya hanya membaca yang diizinkan.
- Pilihan yang dibuat dari elemen kustom (bukan radio/checkbox standar) mungkin tidak terdeteksi, tapi isi layar tetap bisa ditanyakan lewat chat.
- Hemat baterai agresif di sebagian HP (Xiaomi, Oppo, dll) bisa mematikan layanan; atur aplikasi ke "Tanpa batasan".

## Fitur suara
- Di panel robot, tekan tombol 🎙 untuk bertanya dalam Bahasa Indonesia. Ucapan dikenali perangkat, dikirim sebagai pesan chat, lalu jawaban AI dibacakan.
- Tombol 🔊 membacakan ulang jawaban terakhir.
- Akses mikrofon diminta saat pertama dipakai. Jika izin belum diberikan, buka aplikasi Franzz Orbit dan tekan **Aktifkan izin mikrofon**.
- Fitur ini memakai speech recognition dan text-to-speech yang tersedia di perangkat. Ini mode push-to-talk, bukan panggilan audio real-time/full-duplex; beberapa perangkat memerlukan layanan pengenalan suara aktif.
