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
            if os.name == "nt":
                # PyInstaller one-file bootloaders can spawn an inner process.
                # Kill the complete process tree, not only the outer stub.
                subprocess.run(
                    ["taskkill", "/PID", str(process.pid), "/T", "/F"],
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    check=False,
                    timeout=8,
                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0x08000000),
                )
            elif process.poll() is None:
                process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=3)


'''
replace_once(app_file, "def main() -> int:\n", smoke_function + "def main() -> int:\n")
replace_once(
    app_file,
    "def main() -> int:\n    app = QApplication(sys.argv)\n",
    "def main() -> int:\n    if \"--bundle-smoke-test\" in sys.argv[1:]:\n        return _bundle_smoke_test()\n    app = QApplication(sys.argv)\n",
)

ui_file = ROOT / "art" / "ui.py"
replace_once(ui_file, "ART DESKTOP 2.0.1", "ART DESKTOP 2.0.2")
print("ART_ONEFILE_SOURCE_PATCH_OK")
