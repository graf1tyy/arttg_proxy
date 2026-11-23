from __future__ import annotations

import os
import secrets
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def replace_once(path: Path, old: str, new: str) -> None:
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Expected one patch target in {path}, found {count}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


engine_file = ROOT / "art" / "engine.py"
replace_once(
    engine_file,
    """def runtime_root() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent.parent
""",
    """def runtime_root() -> Path:
    if getattr(sys, "frozen", False):
        # PyInstaller --onefile extracts bundled resources to _MEIPASS.
        # The standalone ART-Engine.exe lives inside that private bundle folder.
        bundle_root = getattr(sys, "_MEIPASS", None)
        if bundle_root:
            return Path(bundle_root)
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent.parent
""",
)
replace_once(
    engine_file,
    "Не найден ART-Engine. Убедитесь, что папка engine находится рядом с ART.exe.",
    "Не найден встроенный ART-Engine. Скачайте ART.exe заново из GitHub Releases.",
)

replace_once(
    engine_file,
    "def start_engine(config: AppConfig, log_path: Path, executable: Path | None = None) -> subprocess.Popen:\\n",
    "def _attach_process_tree_job(proc) -> bool:\n    \"\"\"Place the engine and future descendants in a kill-on-close Windows Job.\"\"\"\n    if os.name != \"nt\":\n        return False\n    try:\n        import ctypes\n        from ctypes import wintypes\n\n        class BasicLimitInformation(ctypes.Structure):\n            _fields_ = [\n                (\"PerProcessUserTimeLimit\", ctypes.c_longlong),\n                (\"PerJobUserTimeLimit\", ctypes.c_longlong),\n                (\"LimitFlags\", wintypes.DWORD),\n                (\"MinimumWorkingSetSize\", ctypes.c_size_t),\n                (\"MaximumWorkingSetSize\", ctypes.c_size_t),\n                (\"ActiveProcessLimit\", wintypes.DWORD),\n                (\"Affinity\", ctypes.c_size_t),\n                (\"PriorityClass\", wintypes.DWORD),\n                (\"SchedulingClass\", wintypes.DWORD),\n            ]\n\n        class IoCounters(ctypes.Structure):\n            _fields_ = [\n                (\"ReadOperationCount\", ctypes.c_ulonglong),\n                (\"WriteOperationCount\", ctypes.c_ulonglong),\n                (\"OtherOperationCount\", ctypes.c_ulonglong),\n                (\"ReadTransferCount\", ctypes.c_ulonglong),\n                (\"WriteTransferCount\", ctypes.c_ulonglong),\n                (\"OtherTransferCount\", ctypes.c_ulonglong),\n            ]\n\n        class ExtendedLimitInformation(ctypes.Structure):\n            _fields_ = [\n                (\"BasicLimitInformation\", BasicLimitInformation),\n                (\"IoInfo\", IoCounters),\n                (\"ProcessMemoryLimit\", ctypes.c_size_t),\n                (\"JobMemoryLimit\", ctypes.c_size_t),\n                (\"PeakProcessMemoryUsed\", ctypes.c_size_t),\n                (\"PeakJobMemoryUsed\", ctypes.c_size_t),\n            ]\n\n        kernel32 = ctypes.WinDLL(\"kernel32\", use_last_error=True)\n        kernel32.CreateJobObjectW.argtypes = [wintypes.LPVOID, wintypes.LPCWSTR]\n        kernel32.CreateJobObjectW.restype = wintypes.HANDLE\n        kernel32.SetInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, wintypes.LPVOID, wintypes.DWORD]\n        kernel32.SetInformationJobObject.restype = wintypes.BOOL\n        kernel32.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]\n        kernel32.AssignProcessToJobObject.restype = wintypes.BOOL\n        kernel32.CloseHandle.argtypes = [wintypes.HANDLE]\n        kernel32.CloseHandle.restype = wintypes.BOOL\n\n        job = kernel32.CreateJobObjectW(None, None)\n        if not job:\n            return False\n        limits = ExtendedLimitInformation()\n        limits.BasicLimitInformation.LimitFlags = 0x00002000  # KILL_ON_JOB_CLOSE\n        if not kernel32.SetInformationJobObject(job, 9, ctypes.byref(limits), ctypes.sizeof(limits)):\n            kernel32.CloseHandle(job)\n            return False\n        if not kernel32.AssignProcessToJobObject(job, wintypes.HANDLE(int(proc._handle))):\n            kernel32.CloseHandle(job)\n            return False\n        proc._art_job_handle = job\n        proc._art_job_kernel32 = kernel32\n        return True\n    except Exception:\n        return False\n\n\ndef terminate_engine_process(proc, timeout: float = 5.0) -> None:\n    \"\"\"Terminate the engine process tree even if the one-file parent has exited.\"\"\"\n    if proc is None:\n        return\n    job = getattr(proc, \"_art_job_handle\", None)\n    kernel32 = getattr(proc, \"_art_job_kernel32\", None)\n    try:\n        if job and kernel32 is not None:\n            try:\n                kernel32.TerminateJobObject(job, 1)\n            except Exception:\n                pass\n        elif os.name == \"nt\":\n            try:\n                startup = subprocess.STARTUPINFO()\n                startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW\n                startup.wShowWindow = 0\n                subprocess.run(\n                    [\"taskkill\", \"/PID\", str(proc.pid), \"/T\", \"/F\"],\n                    stdin=subprocess.DEVNULL,\n                    stdout=subprocess.DEVNULL,\n                    stderr=subprocess.DEVNULL,\n                    check=False,\n                    timeout=8,\n                    startupinfo=startup,\n                    creationflags=getattr(subprocess, \"CREATE_NO_WINDOW\", 0x08000000),\n                )\n            except (OSError, subprocess.SubprocessError):\n                pass\n        elif proc.poll() is None:\n            proc.terminate()\n\n        try:\n            proc.wait(timeout=timeout)\n        except subprocess.TimeoutExpired:\n            try:\n                proc.kill()\n                proc.wait(timeout=2.0)\n            except (OSError, subprocess.TimeoutExpired):\n                pass\n    finally:\n        if job and kernel32 is not None:\n            try:\n                kernel32.CloseHandle(job)\n            except Exception:\n                pass\n            proc._art_job_handle = None\n            proc._art_job_kernel32 = None\n\n\n" + "def start_engine(config: AppConfig, log_path: Path, executable: Path | None = None) -> subprocess.Popen:\\n",
)
replace_once(
    engine_file,
    "    proc = subprocess.Popen(command, **kwargs)\\n",
    "    proc = subprocess.Popen(command, **kwargs)\\n    _attach_process_tree_job(proc)\\n",
)

app_file = ROOT / "app.py"
replace_once(
    app_file,
    "import sys\nfrom pathlib import Path\n",
    "import os\nimport secrets\nimport subprocess\nimport sys\nimport time\nfrom pathlib import Path\n",
)
replace_once(
    app_file,
    "from art.config import app_data_dir\n",
    "from art.config import AppConfig, app_data_dir\nfrom art.engine import engine_path, find_free_port, is_local_listener, start_engine\n",
)
smoke_function = '''def _bundle_smoke_test() -> int:
    """Verify the single EXE can find and launch its bundled MTProto engine."""
    binary = engine_path()
    if not binary.is_file():
        print(f"BUNDLE_SMOKE_FAIL: embedded engine missing: {binary}", file=sys.stderr)
        return 41

    icon_path = Path(__file__).resolve().parent / "assets" / "art.ico"
    if not icon_path.is_file():
        print(f"BUNDLE_SMOKE_FAIL: embedded application icon missing: {icon_path}", file=sys.stderr)
        return 42

    config = AppConfig(
        port=find_free_port(28000),
        secret=secrets.token_hex(16),
    ).normalise()
    log_path = app_data_dir() / "onefile-bundle-smoke.log"
    process = None
    try:
        process = start_engine(config, log_path)
        deadline = time.monotonic() + 20.0
        while time.monotonic() < deadline:
            exit_code = process.poll()
            if exit_code is not None:
                print(f"BUNDLE_SMOKE_FAIL: embedded engine exited with {exit_code}", file=sys.stderr)
                return 43
            if is_local_listener(config.host, config.port, timeout=0.25):
                print("ART_ONEFILE_BUNDLE_SMOKE_OK")
                return 0
            time.sleep(0.1)
        print("BUNDLE_SMOKE_FAIL: embedded engine did not open its local port", file=sys.stderr)
        return 44
    except Exception as exc:
        print(f"BUNDLE_SMOKE_FAIL: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 45
    finally:
        if process is not None:
            from art.engine import terminate_engine_process
            terminate_engine_process(process)


'''
replace_once(app_file, "def main() -> int:\n", smoke_function + "def main() -> int:\n")
replace_once(
    app_file,
    "def main() -> int:\n    app = QApplication(sys.argv)\n",
    "def main() -> int:\n    if \"--bundle-smoke-test\" in sys.argv[1:]:\n        return _bundle_smoke_test()\n    app = QApplication(sys.argv)\n",
)

ui_file = ROOT / "art" / "ui.py"
replace_once(ui_file, "ART DESKTOP 2.0.1", "ART DESKTOP 2.0.2")

config_file = ROOT / "art" / "config.py"
replace_once(
    config_file,
    "    auto_refresh_minutes: int = 120\n",
    "    auto_refresh_minutes: int = 120\n    minimize_to_tray_on_close: bool = True\n    start_with_windows: bool = False\n",
)
replace_once(
    config_file,
    "        self.auto_open_telegram = bool(self.auto_open_telegram)\n",
    "        self.auto_open_telegram = bool(self.auto_open_telegram)\n        self.minimize_to_tray_on_close = bool(self.minimize_to_tray_on_close)\n        self.start_with_windows = bool(self.start_with_windows)\n",
)

startup_file = ROOT / "art" / "startup.py"
startup_file.write_text(r'''from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

RUN_KEY = r"Software\\Microsoft\\Windows\\CurrentVersion\\Run"
VALUE_NAME = "ART"


def startup_command() -> str:
    if getattr(sys, "frozen", False):
        command = [str(Path(sys.executable).resolve()), "--autostart"]
    else:
        script = Path(sys.argv[0]).resolve()
        command = [str(Path(sys.executable).resolve()), str(script), "--autostart"]
    return subprocess.list2cmdline(command)


def set_windows_autostart(enabled: bool) -> None:
    if os.name != "nt":
        if enabled:
            raise OSError("Автозапуск ART через реестр доступен только в Windows.")
        return
    import winreg

    with winreg.CreateKeyEx(
        winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE
    ) as key:
        if enabled:
            winreg.SetValueEx(key, VALUE_NAME, 0, winreg.REG_SZ, startup_command())
        else:
            try:
                winreg.DeleteValue(key, VALUE_NAME)
            except FileNotFoundError:
                pass
''', encoding="utf-8")

ui_file = ROOT / "art" / "ui.py"
replace_once(
    ui_file,
    "    QStackedWidget, QTableWidget, QTableWidgetItem, QTextEdit, QVBoxLayout,\n",
    "    QStackedWidget, QTableWidget, QTableWidgetItem, QTextEdit, QVBoxLayout,\n    QMenu, QSystemTrayIcon,\n",
)
replace_once(
    ui_file,
    "        self.config = load_config()\n",
    "        self.config = load_config()\n        self._force_quit = False\n        self._shutdown_started = False\n        self.tray_icon = None\n",
)
replace_once(
    ui_file,
    "        QTimer.singleShot(900, self.refresh_catalog)\n",
    "        QTimer.singleShot(900, self.refresh_catalog)\n        self._init_system_tray()\n",
)
tray_methods = r'''    def _init_system_tray(self) -> bool:
        if not QSystemTrayIcon.isSystemTrayAvailable():
            return False
        self.tray_icon = QSystemTrayIcon(self.windowIcon(), self)
        self.tray_icon.setToolTip("ART — Telegram Route Manager")
        menu = QMenu(self)
        self.tray_open_action = menu.addAction("Открыть ART")
        self.tray_open_action.triggered.connect(self.restore_from_tray)
        self.tray_engine_action = menu.addAction("Запустить мост")
        self.tray_engine_action.triggered.connect(self.toggle_engine_from_tray)
        menu.addSeparator()
        self.tray_exit_action = menu.addAction("Выход")
        self.tray_exit_action.triggered.connect(self.request_app_exit)
        self.tray_icon.setContextMenu(menu)
        self.tray_icon.activated.connect(self._tray_activated)
        self.tray_icon.show()
        return self.tray_icon.isVisible()

    def _tray_activated(self, reason) -> None:
        if reason in (
            QSystemTrayIcon.ActivationReason.Trigger,
            QSystemTrayIcon.ActivationReason.DoubleClick,
        ):
            self.restore_from_tray()

    def minimize_to_tray(self) -> bool:
        if self.tray_icon is not None and self.tray_icon.isVisible():
            self.hide()
            return True
        self.showMinimized()
        return False

    def restore_from_tray(self) -> None:
        self.showNormal()
        self.raise_()
        self.activateWindow()

    def toggle_engine_from_tray(self) -> None:
        running = bool(self.engine_process and self.engine_process.poll() is None)
        if running:
            self.stop_engine()
        else:
            self.start_engine()
        if self.tray_icon is not None:
            self.tray_engine_action.setText("Запустить мост" if running else "Остановить мост")

    def request_app_exit(self) -> None:
        self._force_quit = True
        self.close()
        app = QApplication.instance()
        if app is not None:
            app.quit()

    def shutdown_for_exit(self) -> None:
        if self._shutdown_started:
            return
        self._shutdown_started = True
        if hasattr(self, "timer"):
            self.timer.stop()
        if hasattr(self, "catalog_timer"):
            self.catalog_timer.stop()
        worker = getattr(self, "worker", None)
        if worker is not None and worker.isRunning():
            worker.requestInterruption()
            # Feed downloads and TCP probes have bounded timeouts. Join the
            # worker before Qt destroys the window and Python exits.
            worker.wait(12000)
            if worker.isRunning():
                worker.wait()
        self.stop_engine()
        if self.tray_icon is not None:
            self.tray_icon.hide()

'''
replace_once(ui_file, "    def _build_ui(self) -> None:\n", tray_methods + "    def _build_ui(self) -> None:\n")
replace_once(
    ui_file,
    "        self.auto_open_check = QCheckBox(\"Открывать ссылку подключения Telegram после запуска моста\")\n        self.auto_open_check.setChecked(self.config.auto_open_telegram)\n",
    "        self.auto_open_check = QCheckBox(\"Открывать ссылку подключения Telegram после запуска моста\")\n        self.auto_open_check.setChecked(self.config.auto_open_telegram)\n        self.tray_check = QCheckBox(\"При нажатии на крестик сворачивать ART в системный трей\")\n        self.tray_check.setChecked(self.config.minimize_to_tray_on_close)\n        self.autostart_check = QCheckBox(\"Запускать ART вместе с Windows (в фоне, без открытия Telegram)\")\n        self.autostart_check.setChecked(self.config.start_with_windows)\n",
)
replace_once(
    ui_file,
    "        n.addWidget(self.auto_open_check)\n",
    "        n.addWidget(self.auto_open_check)\n        n.addWidget(self.tray_check)\n        n.addWidget(self.autostart_check)\n",
)
replace_once(
    ui_file,
    "        self.config.auto_open_telegram = self.auto_open_check.isChecked()\n",
    "        self.config.auto_open_telegram = self.auto_open_check.isChecked()\n        self.config.minimize_to_tray_on_close = self.tray_check.isChecked()\n        requested_autostart = self.autostart_check.isChecked()\n        try:\n            from .startup import set_windows_autostart\n            set_windows_autostart(requested_autostart)\n            self.config.start_with_windows = requested_autostart\n        except OSError as exc:\n            self.autostart_check.setChecked(self.config.start_with_windows)\n            QMessageBox.warning(self, \"Автозапуск не изменён\", str(exc))\n",
)
replace_once(
    ui_file,
    "    def closeEvent(self, event) -> None:\n        if self.worker and self.worker.isRunning():\n            self.worker.requestInterruption()\n            self.worker.wait(14000)\n        self.stop_engine()\n        event.accept()\n",
    "    def closeEvent(self, event) -> None:\n        if (not self._force_quit and self.config.minimize_to_tray_on_close\n                and self.tray_icon is not None and self.tray_icon.isVisible()):\n            self.hide()\n            event.ignore()\n            return\n        self.shutdown_for_exit()\n        event.accept()\n",
)

# Ensure the catalog worker never leaves an unbounded feed request during exit.
catalog_file = ROOT / "art" / "catalog.py"
replace_once(catalog_file, "def _download(url: str, timeout: float = 8.0,", "def _download(url: str, timeout: float = 4.0,")
replace_once(catalog_file, "def probe_tcp(candidate: ProxyCandidate, timeout: float = 1.6)", "def probe_tcp(candidate: ProxyCandidate, timeout: float = 1.0)")

# App lifecycle and autostart: close-to-tray does not terminate the Qt process.
replace_once(
    app_file,
    "    app = QApplication(sys.argv)\n",
    "    app = QApplication(sys.argv)\n    app.setQuitOnLastWindowClosed(False)\n",
)
replace_once(
    app_file,
    "        window.show()\n        app.processEvents()\n        window.close()\n",
    "        window._force_quit = True\n        window.show()\n        app.processEvents()\n        window.close()\n",
)
replace_once(
    app_file,
    "    window = MainWindow()\n    if \"--smoke-test\"",
    "    window = MainWindow()\n    app.aboutToQuit.connect(window.shutdown_for_exit)\n    if \"--smoke-test\"",
)
replace_once(
    app_file,
    "    window.show()\n    result = app.exec()\n",
    "    if \"--autostart\" in sys.argv[1:]:\n        window.config.auto_open_telegram = False\n        window.auto_open_pending = False\n        window.minimize_to_tray()\n        window.start_engine()\n    else:\n        window.show()\n    result = app.exec()\n",
)

replace_once(ui_file, "ART DESKTOP 2.0.1", "ART DESKTOP 2.0.3")

print("ART_ONEFILE_SOURCE_PATCH_OK")
