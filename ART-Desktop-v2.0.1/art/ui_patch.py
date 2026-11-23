from __future__ import annotations

import os
import re
import subprocess
from pathlib import Path


def _make_titlebar_icon(kind: str):
    """Draw crisp native-looking title-bar icons without font glyph dependencies."""
    from PySide6.QtCore import Qt
    from PySide6.QtGui import QColor, QIcon, QPainter, QPen, QPixmap

    pixmap = QPixmap(18, 18)
    pixmap.fill(Qt.GlobalColor.transparent)
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    pen = QPen(QColor("#F2F4F7"))
    pen.setWidthF(1.6)
    pen.setCapStyle(Qt.PenCapStyle.RoundCap)
    pen.setJoinStyle(Qt.PenJoinStyle.RoundJoin)
    painter.setPen(pen)
    painter.setBrush(Qt.BrushStyle.NoBrush)

    if kind == "minimize":
        painter.drawLine(3, 9, 15, 9)
    elif kind == "maximize":
        painter.drawRect(4, 4, 10, 10)
    elif kind == "restore":
        painter.drawRect(3, 5, 9, 9)
        painter.drawLine(6, 4, 6, 3)
        painter.drawLine(6, 3, 14, 3)
        painter.drawLine(14, 3, 14, 11)
        painter.drawLine(14, 11, 13, 11)
    elif kind == "close":
        painter.drawLine(4, 4, 14, 14)
        painter.drawLine(14, 4, 4, 14)
    painter.end()
    return QIcon(pixmap)


def _apply_titlebar_icons(window) -> bool:
    """Style only the three direct buttons inside the dedicated title-bar frame."""
    try:
        from PySide6.QtCore import Qt, QSize
        from PySide6.QtWidgets import QFrame, QPushButton

        titlebar = window.findChild(QFrame, "titlebar")
        if titlebar is None:
            return False
        buttons = titlebar.findChildren(
            QPushButton, options=Qt.FindChildOption.FindDirectChildrenOnly
        )
        buttons.sort(key=lambda button: button.geometry().x())
        if len(buttons) != 3:
            return False

        maximize_kind = "restore" if window.isMaximized() else "maximize"
        definitions = (
            ("window_minimize", "minimize", "Свернуть", False),
            ("window_maximize", maximize_kind, "Восстановить" if window.isMaximized() else "Развернуть", False),
            ("window_close", "close", "Закрыть", True),
        )
        for button, (object_name, icon_kind, tip, is_close) in zip(buttons, definitions):
            button.setObjectName(object_name)
            button.setText("")
            button.setIcon(_make_titlebar_icon(icon_kind))
            button.setIconSize(QSize(14, 14))
            button.setFixedSize(30, 28)
            button.setToolTip(tip)
            button.setAccessibleName(tip)
            button.setVisible(True)
            button.setEnabled(True)
            if is_close:
                button.setStyleSheet(
                    "QPushButton { background:#20242d; border:1px solid #303744; "
                    "border-radius:9px; padding:0; }"
                    "QPushButton:hover { background:#5a252b; border:1px solid #a64750; }"
                    "QPushButton:pressed { background:#3d1c21; }"
                )
            else:
                button.setStyleSheet(
                    "QPushButton { background:#20242d; border:1px solid #303744; "
                    "border-radius:9px; padding:0; }"
                    "QPushButton:hover { background:#323945; border:1px solid #586273; }"
                    "QPushButton:pressed { background:#171b22; }"
                )
        return True
    except Exception:
        return False

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
    """Prefer the upstream's port-80 CF fallback on networks resetting TLS/443."""
    try:
        setattr(config, "_art_force_no_secure", True)
    except Exception:
        pass


def _kill_engine_tree(proc) -> None:
    """Stop the PyInstaller engine and all descendants on Windows."""
    if proc is None or proc.poll() is not None or os.name != "nt":
        return
    try:
        startup = subprocess.STARTUPINFO()
        startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        startup.wShowWindow = 0
        subprocess.run(
            ["taskkill", "/PID", str(proc.pid), "/T", "/F"],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
            timeout=8,
            startupinfo=startup,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0x08000000),
        )
    except (OSError, subprocess.SubprocessError):
        pass


def _current_start_reports_listener(window, host: str, port: int) -> bool:
    """Use the engine's flushed bind-success line as a fallback readiness signal."""
    try:
        proc = getattr(window, "engine_process", None)
        if proc is None or proc.poll() is not None:
            return False
        log_path = Path(__import__("art.config", fromlist=["app_data_dir"]).app_data_dir()) / "engine.log"
        raw = log_path.read_text(encoding="utf-8", errors="replace")
        offset = int(getattr(window, "_art_engine_log_offset", 0))
        tail = raw[offset:]
        pattern = re.compile(
            rf"Listening on\s+{re.escape(host)}:{int(port)}(?:\s|$)",
            re.IGNORECASE,
        )
        return any(pattern.search(line) for line in tail.splitlines())
    except Exception:
        return False


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
    original_start_engine = MainWindow.start_engine
    original_stop_engine = MainWindow.stop_engine
    original_toggle_maximize = MainWindow._toggle_maximize

    # Patch the engine argument builder at its defining module: start_engine()
    # resolves this global when it builds the one-shot stdin configuration.
    try:
        import art.engine as engine_module
        original_build_args = engine_module.build_engine_args
        if not getattr(engine_module, "_art_no_secure_patch", False):
            def build_args(config, log_path):
                args = original_build_args(config, log_path)
                if getattr(config, "_art_force_no_secure", False) and "--no-secure" not in args:
                    args.append("--no-secure")
                return args
            engine_module.build_engine_args = build_args
            engine_module._art_no_secure_patch = True
    except Exception:
        pass

    # The socket probe can be transiently missed on Windows while the
    # PyInstaller one-file child is starting. Trust the current process's
    # explicit "Listening on host:port" log only for this launch.
    try:
        import art.ui as ui_module
        original_listener_probe = ui_module.is_local_listener
        if not getattr(ui_module, "_art_listener_probe_patch", False):
            def listener_probe(host, port, timeout=0.35):
                if original_listener_probe(host, port, timeout):
                    return True
                try:
                    window = getattr(ui_module, "_art_active_window", None)
                    if window is not None and _current_start_reports_listener(window, host, port):
                        return True
                except Exception:
                    pass
                return False
            ui_module.is_local_listener = listener_probe
            ui_module._art_listener_probe_patch = True
    except Exception:
        pass

    def init(self, *args, **kwargs):
        original_init(self, *args, **kwargs)
        try:
            _set_no_secure(self.config)
            import art.ui as ui_module
            ui_module._art_active_window = self
        except Exception:
            pass
        self._art_engine_log_offset = 0
        _apply_titlebar_icons(self)

    def toggle_maximize(self):
        original_toggle_maximize(self)
        _apply_titlebar_icons(self)

    def start_engine(self):
        try:
            from art.config import app_data_dir
            path = app_data_dir() / "engine.log"
            self._art_engine_log_offset = len(path.read_text(encoding="utf-8", errors="replace")) if path.exists() else 0
        except Exception:
            self._art_engine_log_offset = 0
        return original_start_engine(self)

    def stop_engine(self):
        proc = getattr(self, "engine_process", None)
        if proc is not None:
            try:
                from art.engine import terminate_engine_process
                terminate_engine_process(proc)
            except Exception:
                _kill_engine_tree(proc)
        return original_stop_engine(self)

    def apply_state(self, online, headline=None, detail=None):
        original_apply_state(self, online, headline, detail)
        if online:
            self.connect_btn.setVisible(True)
            self.connect_btn.setEnabled(True)
            self.hero_state.setText("Транспорт готов")
            self.hero_desc.setText(
                "ART Engine запущен. Telegram использует локальный MTProto-мост, "
                "а ART автоматически выбирает доступный транспорт."
            )
        else:
            self.connect_btn.setVisible(True)

    def poll_engine(self):
        original_poll_engine(self)
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
    MainWindow.start_engine = start_engine
    MainWindow.stop_engine = stop_engine
    MainWindow._toggle_maximize = toggle_maximize
    MainWindow._apply_state = apply_state
    MainWindow._poll_engine = poll_engine
    MainWindow._art_runtime_patch = True
