from __future__ import annotations

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


def _set_no_secure(config) -> None:
    """Prefer HTTP/CF port 80 fallback for networks that reset CF TLS."""
    for name in ("no_secure", "disable_secure"):
        if hasattr(config, name):
            try:
                setattr(config, name, True)
            except Exception:
                pass


def _fix_window_controls(window) -> None:
    """Restore visible native-style minimize/maximize/close glyphs."""
    try:
        from PySide6.QtCore import Qt
        from PySide6.QtWidgets import QToolButton

        buttons = []
        for b in window.findChildren(QToolButton):
            g = b.geometry()
            if g.width() >= 20 and g.height() >= 20 and g.top() < 70:
                buttons.append(b)
        buttons.sort(key=lambda b: b.geometry().x())
        if len(buttons) >= 3:
            glyphs = ("—", "□", "×")
            for b, glyph in zip(buttons[-3:], glyphs):
                b.setText(glyph)
                b.setToolButtonStyle(Qt.ToolButtonTextOnly)
                b.setMinimumSize(36, 30)
                b.setStyleSheet(
                    "QToolButton { border: 0; background: transparent; "
                    "font-size: 18px; font-weight: 500; padding: 0; }"
                    "QToolButton:hover { background: rgba(255,255,255,0.08); }"
                )
                b.setToolTip({"—": "Свернуть", "□": "Развернуть", "×": "Закрыть"}[glyph])
    except Exception:
        pass


def install_ui_patch() -> None:
    """Fix live dashboard telemetry, title-bar controls and CF fallback defaults."""
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
            _set_no_secure(self.config)
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
