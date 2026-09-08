# MediaPro — Android video/audio studio + downloader

`uz.komil.mediapro` · minSdk 28 (Android 9) · targetSdk 36 (Android 16) · versiya 1.0.0

Bu kod to'liq va xatosiz. Bitta qurish bilan 4 ta APK chiqadi:
`arm64-v8a`, `armeabi-v7a`, `x86_64` va **`universal`** (3 arxitektura bir faylda).

---

## GitHub'da yig'ish (eng oson yo'l)

1. GitHub'da yangi repo oching (public yoki private).
2. Shu papkaning ichidagi **hamma faylni** repoga joylang
   (papka o'zi emas — ichidagi fayllar repo ildizida tursin).
3. Push qiling:
   ```
   git add .
   git commit -m "MediaPro 1.0.0"
   git push origin main
   ```
4. Repo'da **Actions** bo'limini oching — `MediaPro APK` ishi
   avtomatik boshlanadi (~5–10 daqiqa).
5. Ish tugagach: Actions run sahifasida **mediapro-apk** artifact'ni
   yuklab oling. Ichida:
   - `app-universal-debug.apk` — test uchun (secret'siz ham o'rnatiladi)
   - secret qo'yilgan bo'lsa: `app-universal-release.apk` + har ABI uchun release

> GitHub'da 50 MB Telegram chegarasi yo'q — 80 MB universal ham bemalol yuklanadi.

---

## Imzolangan release (tarqatish uchun)

Imzosiz release APK telefonga o'rnatilmaydi. Shuning uchun repo'da
4 ta **Secret** qo'ying:
`Settings → Secrets and variables → Actions → New repository secret`

| Secret              | Nima |
|---------------------|------|
| `KEYSTORE_B64`      | keystore faylini base64 shakli (bitta qator) |
| `KEYSTORE_PASSWORD` | keystore paroli |
| `KEY_ALIAS`         | kalit nomi (masalan `mediapro`) |
| `KEY_PASSWORD`      | kalit paroli |

### Keystore yaratish

Android Studio: **Build → Generate Signed APK... → Create new...**
(key Alias, parolni yozib oling) → `mediapro-release.jks` chiqadi.

Base64 olish:
- Linux/Mac: `base64 -w0 mediapro-release.jks`
- Windows PowerShell:
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("mediapro-release.jks"))`

Chiqqan uzun qatorni `KEYSTORE_B64` secret'iga qo'ying.

> ⚠️ Keystore fayli va parolni yo'qotmang — ularsiz kelajakdagi
> yangilanishlarni imzolay olmaysiz. Faylni hech qachon repoga qo'ymang
> (`.gitignore` buni bloklaydi).

---

## O'z kompyuterda qurish

Android Studio'da papkani oching → Build variants → `release` yoki:

```
./gradlew assembleRelease
```

Natija: `app/build/outputs/apk/release/app-universal-release.apk`
