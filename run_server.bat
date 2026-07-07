@echo off
setlocal
cd /d "%~dp0"

echo ==========================================
echo Starting Ren Backend and Ngrok Tunnel
echo URL: https://unpermanent-obcordate-maurice.ngrok-free.app
echo ==========================================

:: Start the FastAPI backend on port 80
:: Note: This may require Administrator privileges to bind to port 80
start "Ren Backend" cmd /k "python -m uvicorn app.main:app --app-dir backend --host 0.0.0.0 --port 80"

:: Start the ngrok tunnel
start "ngrok Tunnel" cmd /k "ngrok http --url=unpermanent-obcordate-maurice.ngrok-free.app 80"

echo.
echo Launching... Keep both windows open to maintain the connection.
echo.
pause