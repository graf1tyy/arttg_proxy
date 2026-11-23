# ART Mobile

ART Mobile is the first Android client built around the shared ART Kotlin core.

## First preview scope

- Downloads the maintained `best.txt` and `stable.txt` MTProto feeds.
- Parses and deduplicates Telegram proxy links using the shared Kotlin core.
- Enriches candidates from the upstream `endpoints.json` metadata, including sponsor status, success rate and RTT.
- Performs a bounded TCP reachability check against the feed's published proxy endpoints.
- Excludes proxies whose metadata explicitly reports a sponsor channel.
- Keeps unknown ad-check status visible as a warning; unknown is not shown as clean.
- Opens the selected `tg://proxy` link in Telegram. Telegram asks the user to add/enable that proxy.

## Current limitation

This preview does **not** create an Android system VPN and does not route TikTok or every phone app. It hands an MTProto proxy to Telegram. A separate Android `VpnService` plus an Android-native transport engine is required before ART can act as a system-wide VPN.

Public proxies are third-party infrastructure. They can observe connection metadata and may stop working without notice. Use only if you accept that risk.

## Build

Requires JDK 17, Android SDK 36 and Gradle 8.13.

```sh
gradle --no-daemon :app:assembleRelease
```
