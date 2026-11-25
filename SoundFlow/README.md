# 🎵 SoundFlow - Smart Audio Player & YouTube Streamer

Aplikasi pemutar musik Android yang simpel, elegan, dan dirancang khusus sesuai kebutuhan:
1. **Lagu Lokal Pintar**: Memindai file musik di memori internal/SD Card HP dan **menyaring secara otomatis** audio dari WhatsApp (Voice Notes / PTT / Audio), Telegram, rekaman suara, serta nada dering/alarm sistem.
2. **Mode Stream YouTube**: Cari jutaan lagu langsung dari YouTube tanpa jeda iklan.
3. **Opsi Download Offline**: Tersedia tombol download satu ketukan di setiap lagu streaming. File tersimpan langsung ke folder `Music/SoundFlow` dan otomatis terdeteksi di tab Lagu Lokal.
4. **Anti-Mati di Layar Belakang**: Musik **tetap menyala saat layar dimatikan** atau saat aplikasi diminimize/dikeluarkan berkat integrasi **Media3 ExoPlayer & MediaSessionService**.
5. **Playlist & Favorit**: Kelola playlist musik sesuai keinginanmu.

---

## 📱 Struktur Navigasi Aplikasi

- **Tab 1: Lagu HP (Offline)**: Menampilkan seluruh musik yang ada di penyimpanan HP. Dilengkapi search bar instan dan filter otomatis (VN WA & audio pendek < 45 detik langsung dieliminasi).
- **Tab 2: Stream YouTube (Online)**: Cari lagu apa saja. Bisa langsung didengarkan streaming dan ada tombol unduh untuk disimpan permanen ke HP.
- **Tab 3: Playlist**: Bikin playlist baru untuk mengelompokkan lagu-lagu favorit.
- **Bottom Mini Player & Full Player**: Mini player mengambang di bawah layar yang bisa dibuka menjadi tampilan Now Playing berukuran penuh dengan cover album, seekbar durasi, dan tombol kontrol.

---

## 🛠️ Arsitektur Teknis

- **Bahasa**: Kotlin (100% Native Android)
- **UI Toolkit**: Jetpack Compose + Material 3 (Dark/AMOLED theme)
- **Audio Engine**: AndroidX Media3 (ExoPlayer + MediaSessionService + Foreground Service)
- **Audio Scanner**: Android MediaStore ContentResolver dengan filter cerdas:
  - `IS_MUSIC != 0`
  - `DURATION >= 45.000 ms`
  - Blacklist path `Android/media/com.whatsapp`, `Voice Notes`, `Ringtones`, dll.
- **Database**: Android Jetpack Room Database (Penyimpanan Playlist & Favorite)
- **Remote Streamer & Downloader**: Piped & Invidious Public API Engine + OkHttp Streaming Downloader

---

## 🚀 Cara Build Menjadi File APK

Ada dua cara mudah untuk menghasilkan file `.apk` agar bisa langsung dipasang di HP:

### Cara 1: Menggunakan GitHub Actions (Otomatis & Tanpa Install Apapun di PC) — *Paling Mudah*
1. Upload/Push folder proyek ini ke akun GitHub milikmu.
2. Buka tab **Actions** di repositori GitHub tersebut.
3. Alur kerja `Build SoundFlow APK` akan otomatis berjalan dan meng-compile APK di cloud dalam waktu ~2 menit.
4. Setelah selesai, download file **SoundFlow-Debug-APK** di bagian *Artifacts*, kirim file `.apk` ke HP, dan instal!

### Cara 2: Menggunakan Android Studio di PC
1. Buka software **Android Studio**.
2. Pilih **Open**, lalu arahkan ke folder:
   `C:\Users\G-force\.gemini\antigravity\scratch\SoundFlow`
3. Tunggu hingga Gradle selesai melakukan sinkronisasi dependensi.
4. Hubungkan HP via kabel data USB (aktifkan USB Debugging) lalu klik tombol **Run** (segitiga hijau) untuk langsung memasang ke HP.
   - Atau klik menu **Build > Build Bundle(s) / APK(s) > Build APK(s)** untuk menghasilkan file `.apk`.
