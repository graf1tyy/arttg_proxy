from __future__ import annotations

import os
import re
from pathlib import Path

_STATS_RE = re.compile(r"stats:\s+(?P<body>.+)$")
_PAIR_RE = re.compile(r"(?P<key>[A-Za-z_]+)=(?P<value>[^\s]+)")


def _read_latest_stats() -> dict[str, str]:
    try:
        from art.config import app_data_dir
        log_path = Path(app_data_dir()) / "engine.log"
        if not log_path.exists():
            return {}
        raw = log_path.read_text(encoding="utf-8", errors="replace")[-128 * 1024:]
        for line in reversed(raw.splitlines()):
            match = _STATS_RE.search(line)
            if match:
                return {m.group("key"): m.group("value") for m in _PAIR_RE.finditer(match.group("body"))}
    except Exception:
        pass
    return {}


def _fix_window_controls(window) -> None:
    """Make the existing frameless title-bar buttons visibly glyph-based."""
    try:
        from PySide6.QtCore import Qt
        from PySide6.QtWidgets import QAbstractButton

        candidates = []
        width = max(300, window.width())
        for b in window.findChildren(QAbstractButton):
            g = b.geometry()
            if g.width() < 18 or g.height() < 18:
                continue
            if g.y() > 72 or g.x() < max(0, width - 240):
                continue
            candidates.append(b)

        # The right-most three title-bar buttons are minimize/maximize/close.
        candidates.sort(key=lambda b: b.geometry().x())
        if len(candidates) >= 3:
            buttons = candidates[-3:]
            glyphs = ("−", "□", "×")
            tips = ("Свернуть", "Развернуть", "Закрыть")
            for b, glyph, tip in zip(buttons, glyphs, tips):
                try:
                    b.setIcon(b.style().standardIcon(
                        b.style().SP_TitleBarMinButton if glyph == "−"
                        else b.style().SP_TitleBarMaxButton if glyph == "□"
                        else b.style().SP_TitleBarCloseButton
                    ))
                except Exception:
                    pass
                # Keep text as a guaranteed fallback if the icon is blank/missing.
                b.setText(glyph)
                b.setToolButtonStyle(Qt.ToolButtonTextOnly)
                b.setMinimumSize(40, 32)
                b.setMaximumHeight(36)
                b.setStyleSheet(
                    "QAbstractButton { border: 0; background: transparent; "
                    "color: #ffffff; font-size: 18px; font-weight: 500; "
                    "padding: 0; margin: 0; }"
                    "QAbstractButton:hover { background: rgba(255,255,255,0.10); }"
                )
                b.setToolTip(tip)
                b.setAccessibleName(tip)
    except Exception:
        pass


def _tune_engine_config(config) -> None:
    """Prefer WS for media and use plaintext CF fallback where TLS is blocked."""
    for name, value in (
        ("h2_enabled", False),
        ("cfproxy_h2_media", False),
        ("no_h2", True),
        ("disable_secure", True),
        ("no_secure", True),
        ("pool_size", 8),
    ):
        if hasattr(config, name):
            try:
                setattr(config, name, value)
            except Exception:
                pass


def install_ui_patch() -> None:
    """ART runtime UI/transport patch."""
    try:
        from art.ui import MainWindow
    except Exception:
        return

    if getattr(MainWindow, "_art_runtime_patch", False):
        return

    original_init = MainWindow.__init__
    original_apply_state = MainWindow._apply_state
    original_poll_engine = MainWindow._poll_engine

    def init(self, *args, **kwargs):
        original_init(self, *args, **kwargs)
        try:
            _tune_engine_config(self.config)
        except Exception:
            pass
        _fix_window_controls(self)

    def apply_state(self, online, headline=None, detail=None):
        original_apply_state(self, online, headline, detail)
        _fix_window_controls(self)
        if online:
            self.hero_state.setText("Транспорт готов")
            self.hero_desc.setText(
                "ART Engine запущен. Telegram использует локальный MTProto-мост, "
                "а ART автоматически выбирает доступный транспорт."
            )
            self.connect_btn.setVisible(False)
        else:
            self.connect_btn.setVisible(True)

    def poll_engine(self):
        original_poll_engine(self)
        _fix_window_controls(self)
        stats = _read_latest_stats()
        if not stats:
            return

        def set_value(widget_name: str, value: str) -> None:
            widget = getattr(self, widget_name, None)
            if widget is not None and hasattr(widget, "value_label"):
                widget.value_label.setText(value)

        ws = stats.get("ws", "0")
        cf = stats.get("cf", "0")
        tcp = stats.get("tcp_fb", "0")
        h2 = stats.get("h2", "0")
        h2_err = stats.get("h2_err", "0")
        up = stats.get("up", "—")
        down = stats.get("down", "—")

        set_value("stat_engine", f"WS {ws} · CF {cf} · TCP {tcp}")
        set_value("stat_media", f"HTTP/2: {h2} · ошибки: {h2_err}")
        set_value("stat_proxy", f"↑ {up} · ↓ {down}")

        route_parts = []
        if int(ws or 0) > 0:
            route_parts.append("WebSocket pool")
        if int(cf or 0) > 0:
            route_parts.append("CF fallback")
        if int(tcp or 0) > 0:
            route_parts.append("TCP fallback")
        if route_parts:
            self.route_value.setText(" · ".join(route_parts))

        try:
            self.local_port.setText(f"127.0.0.1:{int(self.config.port)}")
        except Exception:
            pass

    MainWindow.__init__ = init
    MainWindow._apply_state = apply_state
    MainWindow._poll_engine = poll_engine
    MainWindow._art_runtime_patch = True
