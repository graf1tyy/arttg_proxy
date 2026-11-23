# ART Kotlin core

The Kotlin migration starts as a shared, platform-neutral core. It does not replace the working Windows transport engine yet.

## Current scope

- Common Kotlin models and validated ART settings.
- MTProto link/plain-text feed parsing.
- Proxy deduplication across sources.
- Conservative automatic eligibility: a proxy with unknown sponsor status is not treated as clean.
- Ranking that values media success and throughput much more than raw ping.
- Shared tests executed on every change through GitHub Actions.

## Architecture boundary

The core deliberately does not own HTTP networking, registry access, Windows tray integration, Android foreground services, or VPN/TUN permissions. Those are platform adapters. The same candidate, catalogue, and ranking rules will be consumed by the Android app and the Windows shell after the core is proven.

## Build

Requires JDK 17 and Gradle 9.2.1 or compatible.

```sh
gradle --no-daemon :core:jvmTest
```

Kotlin Multiplatform plugin: 2.4.21.
