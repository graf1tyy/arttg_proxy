# Third-party notices

## TgwsProxyAndroid

The Android native transport core in `native/tgwsproxy` is based on source from
[TgwsProxyAndroid](https://github.com/wqeww0001/TgwsProxyAndroid).

- Upstream project: https://github.com/wqeww0001/TgwsProxyAndroid
- License: GNU General Public License v3.0 (GPL-3.0)
- Source files: `native/tgwsproxy/src/*.rs`, `Cargo.toml`, and `Cargo.lock`
- The corresponding license text is included in [LICENSE-GPLv3](native/tgwsproxy/LICENSE-GPLv3).

## Flowseal TG WS Proxy

The upstream Android transport identifies and includes components derived from
[Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy). Its MIT notice
is preserved in the included license bundle.

## Modifications in ARTproxy

ARTproxy integrates the native core with a new minimal Android service and UI.
Local bridge lifecycle, app branding, Android manifest, and GitHub Actions build
workflow are adapted for this repository.

The original upstream notices and license terms apply to the copied transport
components. The Android transport must remain distributable under GPL-3.0.
