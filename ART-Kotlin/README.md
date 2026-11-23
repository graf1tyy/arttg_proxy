# ARTproxy for Android

Android-клиент Telegram с локальным ART-мостом на Rust/Tokio.

- Не скачивает публичные списки MTProto-прокси.
- Запускает локальный адрес `127.0.0.1:1443` (или свободный порт, если стандартный занят).
- Использует транспорт WebSocket/WSS с Cloudflare и TCP fallback.
- Подключает Telegram к локальному мосту через системную ссылку `tg://proxy`.
- Не является системным VPN.

## Сборка

Требования: JDK 17, Android SDK 36, NDK 27.2.12479018, Rust stable и cargo-ndk.

```bash
cd native/tgwsproxy
cargo ndk --platform 26 -t arm64-v8a -t armeabi-v7a -t x86_64 \
  -o ../../app/src/main/jniLibs build --release --locked

cd ../..
gradle --no-daemon :app:testReleaseUnitTest :app:assembleRelease
```

Готовый APK: `app/build/outputs/apk/release/app-release.apk`.

## Лицензии

См. [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) и [LICENSE-GPLv3](native/tgwsproxy/LICENSE-GPLv3).
