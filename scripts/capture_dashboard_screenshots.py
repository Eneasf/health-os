#!/usr/bin/env python3
"""
Automated Dashboard Screenshot Capture Engine

Uses Headless Google Chrome to render and capture high-resolution,
light-mode screenshots of each Clinical Command Center workspace and mobile view
using the generated synthetic mock HTML previews.
"""

import os
import sys
import time
import shutil
import subprocess
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
CHROME_BIN = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
IMAGES_DIR = BASE_DIR / "docs" / "images"
DASHBOARD_DIR = BASE_DIR / "dashboard"
TEMP_USER_DATA = Path("/tmp/chrome_screenshot_profile")

TARGETS = [
    {
        "name": "01_clinical_command_center_adaptation.png",
        "html": DASHBOARD_DIR / "preview_mock_adaptation.html",
        "width": 1440,
        "height": 900,
        "description": "Workspace 1: Physical Adaptation Triad (Body Comp, Nutrition, Exercise)"
    },
    {
        "name": "02_autonomic_recovery_sleep.png",
        "html": DASHBOARD_DIR / "preview_mock_recovery.html",
        "width": 1440,
        "height": 900,
        "description": "Workspace 2: Autonomic Corridor, HRR-60 & Sleep Architecture"
    },
    {
        "name": "03_longitudinal_bloodwork_matrix.png",
        "html": DASHBOARD_DIR / "preview_mock_bloodwork.html",
        "width": 1440,
        "height": 900,
        "description": "Workspace 3: Longitudinal Bloodwork Command Matrix & Doctor Brief"
    },
    {
        "name": "04_history_life_events.png",
        "html": DASHBOARD_DIR / "preview_mock_history.html",
        "width": 1440,
        "height": 900,
        "description": "Workspace 4: History & Contextual Life Events Superimposition"
    },
    {
        "name": "05_protocol_studio.png",
        "html": DASHBOARD_DIR / "preview_mock_protocol.html",
        "width": 1440,
        "height": 900,
        "description": "Workspace 5: Protocol Studio & Active Multi-Vector Regimens"
    },
    {
        "name": "06_mobile_viewport_dock.png",
        "html": DASHBOARD_DIR / "preview_mock_mobile.html",
        "width": 390,
        "height": 844,
        "description": "Mobile Viewport (iPhone 390x844) with 5-Tab Mobile Navigation Dock"
    }
]


def capture_all():
    if not os.path.exists(CHROME_BIN):
        print(f"Error: Chrome binary not found at {CHROME_BIN}")
        sys.exit(1)

    IMAGES_DIR.mkdir(parents=True, exist_ok=True)

    if TEMP_USER_DATA.exists():
        shutil.rmtree(TEMP_USER_DATA, ignore_errors=True)
    TEMP_USER_DATA.mkdir(parents=True, exist_ok=True)

    success_count = 0

    for item in TARGETS:
        out_file = IMAGES_DIR / item["name"]
        html_file = item["html"]

        if not html_file.exists():
            print(f"Error: Preview HTML file not found: {html_file}")
            continue

        file_url = f"file://{html_file.resolve()}"
        print(f"Capturing: {item['name']} ({item['description']})...")

        cmd = [
            CHROME_BIN,
            "--headless=new",
            "--disable-gpu",
            "--no-first-run",
            "--no-default-browser-check",
            "--hide-scrollbars",
            f"--user-data-dir={TEMP_USER_DATA}",
            f"--window-size={item['width']},{item['height']}",
            f"--screenshot={out_file}",
            file_url
        ]

        try:
            if out_file.exists():
                out_file.unlink()
            proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            
            # Poll for file creation and stabilization (up to 10 seconds)
            captured = False
            for _ in range(20):
                time.sleep(0.5)
                if out_file.exists() and out_file.stat().st_size > 10000:
                    time.sleep(0.5)  # Allow final buffer flush
                    captured = True
                    break

            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    proc.kill()

            if captured and out_file.exists() and out_file.stat().st_size > 1000:
                size_kb = round(out_file.stat().st_size / 1024, 1)
                print(f"  ✓ Success: {out_file.name} ({size_kb} KB)")
                success_count += 1
            else:
                print(f"  ✗ Failed: Output file not created or too small.")
        except Exception as e:
            print(f"  ✗ Exception: {e}")

    # Clean up profile
    shutil.rmtree(TEMP_USER_DATA, ignore_errors=True)

    print(f"\nCompleted: {success_count}/{len(TARGETS)} screenshots successfully captured in {IMAGES_DIR}.")
    return success_count == len(TARGETS)


if __name__ == "__main__":
    success = capture_all()
    sys.exit(0 if success else 1)
