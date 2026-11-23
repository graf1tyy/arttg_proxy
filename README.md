# ARTproxy

Лёгкое Android-приложение для проверки доступных серверов Telegram и передачи выбранного сервера в Telegram.

### Возможности preview

- Загружать обновляемые MTProto-списки.
- Проверять доступность серверов и ранжировать кандидатов.
- Исключать серверы, явно помеченные источником как рекламные.
- Открывать выбранный сервер в Telegram.

### Важно

Это ранняя preview-версия. Сейчас она использует публичные MTProto-источники и передаёт выбранный прокси в Telegram. **Собственный ART-транспорт для Android и системный VPN пока не реализованы.** Не следует считать эту версию полноценным VPN-клиентом.

### Скачать

[Android preview — GitHub Releases](https://github.com/graf1tyy/arttg_proxy/releases/tag/mobile-v0.2.0)

### Сборка

Нужны JDK 17, Android SDK 36 и Gradle 8.13.

```sh
cd ART-Kotlin
gradle --no-daemon :app:assembleRelease
```
