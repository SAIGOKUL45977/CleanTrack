@echo off
setlocal
cd /d "%~dp0"
if not exist "officer-dashboard\index.html" (
  echo Extract the complete CleanTrack package before running this file.
  pause
  exit /b 1
)
where py >nul 2>nul
if not errorlevel 1 (
  set "CLEANTRACK_PYTHON=py"
) else (
  where python >nul 2>nul
  if errorlevel 1 (
    echo Python 3 is required. Install it, then reopen this file.
    pause
    exit /b 1
  )
  set "CLEANTRACK_PYTHON=python"
)
echo Open http://localhost:8000 in your laptop browser.
echo Keep this window open. Press Ctrl+C to stop the server.
%CLEANTRACK_PYTHON% -m http.server 8000 --bind 127.0.0.1 --directory officer-dashboard
pause
