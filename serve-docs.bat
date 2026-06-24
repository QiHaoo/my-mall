@echo off
REM ============================================================
REM  My-Mall docs site local preview
REM  Double-click to run, or: serve-docs.bat
REM  Listens on 0.0.0.0:8000 (LAN accessible for tablet/phone)
REM  URL: http://localhost:8000/my-mall/
REM ============================================================

setlocal
cd /d "%~dp0"

echo.
echo === My-Mall Docs Site ===
echo.

REM --- locate python ---
set "PY="
where py >nul 2>nul && set "PY=py -3"
if not defined PY (
    where python >nul 2>nul && set "PY=python"
)
if not defined PY (
    echo [ERROR] Python not found. Install Python 3.8+ from https://www.python.org/downloads/
    pause
    exit /b 1
)

REM --- ensure mkdocs installed ---
%PY% -m mkdocs --version >nul 2>nul
if errorlevel 1 (
    echo [INIT] First run, installing mkdocs-material ...
    %PY% -m pip install --user mkdocs-material
    if errorlevel 1 (
        echo [ERROR] mkdocs-material install failed. Check network / pip config.
        pause
        exit /b 1
    )
    echo [INIT] Done.
    echo.
)

echo Starting docs site ...
echo.
echo   Local:    http://localhost:8000/my-mall/
echo   LAN:      http://YOUR_IP:8000/my-mall/   (tablet/phone on same WiFi)
echo.
echo   Edits in docs/ hot-reload automatically. Press Ctrl+C to stop.
echo.

%PY% -m mkdocs serve --dev-addr 0.0.0.0:8000

if errorlevel 1 pause
endlocal
