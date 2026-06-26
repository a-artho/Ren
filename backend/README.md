# Ren backend

A FastAPI + Uvicorn server providing AI study plan generation services via the Gemini API.

## Setup & Running the Server

1. **Configure Environment Variables**:
   Copy `backend/.env.example` to `backend/.env`, add your Gemini API key, and configure variables as needed:
   ```env
   GEMINI_API_KEY=your_key_here
   GEMINI_MODEL=gemini-3.1-flash-lite
   REN_DATA_DIR=data
   ```

2. **Start the Server**:
   Run the following command from the **repository root**:
   ```powershell
   uvicorn app.main:app --app-dir backend --reload
   ```
   Alternatively, you can run it from the `backend/` directory directly:
   ```powershell
   cd backend
   .\.venv\Scripts\Activate.ps1   # On Windows
   uvicorn app.main:app --reload
   ```

`REN_DATA_DIR=data` stores SQLite data and temporary uploads under the backend directory. Relative paths are resolved from `backend/`; absolute paths are also accepted. The folder is created automatically and ignored by Git. Uploaded PDFs are capped at 25 MiB and removed after processing.

---

## Connecting from Android Devices

The Android application determines the base URL dynamically based on whether you are running on an emulator or a physical device:

### 1. Android Emulator
* **Base URL**: `http://10.0.2.2:8000` (auto-resolved)
* **Status**: Works out of the box. Android Emulator routes `10.0.2.2` directly to your development machine's `127.0.0.1`.

### 2. Physical Device (e.g., via USB debugging)
* **Base URL**: `http://127.0.0.1:8000` (auto-resolved)
* **Requirement**: You **must** forward port 8000 from the device to your development computer. Run the following command in your terminal before launching the app:
  ```powershell
  adb reverse tcp:8000 tcp:8000
  ```
  This tunnels requests from the phone's loopback interface on port 8000 directly to your local FastAPI server.
