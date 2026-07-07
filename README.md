# Ren

A time-management study planner that transforms lecture materials into structured, adaptable daily study sessions. Upload your PDFs, and Ren extracts the content, builds a deadline-anchored schedule, and supports you through every study session -- without requiring manual task entry.

Ren was developed as part of the Human-Computer Interaction course project at Sapienza University of Rome (2025/2026), under the supervision of Prof. Emanuele Panizzi.

## How It Works

Ren is not an AI study planner. AI (Google Gemini) is used only to extract topics, study blocks, and page ranges from uploaded lecture PDFs. Everything else -- scheduling, feasibility analysis, adaptation, and task state management -- runs locally on the device.

The core flow:

```text
Upload PDFs -> Content extraction -> Deadline-anchored schedule -> Daily adaptive sessions
```

A student uploads 1-10 lecture PDFs, completes a five-step setup wizard (material selection, plan name, deadline, daily time, study days), and receives a day-by-day study plan with workload estimates and feasibility assessment. The Today screen becomes the daily operational surface: reorder, defer, pull in, or remove tasks with live impact preview on the overall plan. The plan adapts around the student's decisions -- it is not a fixed schedule.

## Features

- PDF upload with inline validation (duplicate detection, size limits, corrupted file detection)
- Five-step setup wizard with progressive disclosure
- Content extraction from lecture PDFs via Google Gemini
- Deadline-anchored scheduling with feasibility analysis
- Study plan visualization (Material view by topic, Tree view by schedule day)
- Today screen with drag-to-reorder, defer, pull-in, remove, and live impact preview
- Adaptive focus timer for structured study sessions
- Dedicated Focus Mode screen
- Progress tab with weekly completion charts and consistency tracking
- Local persistence via Room (Android) and SQLite (backend)

## Architecture

```
Android App (Kotlin/Jetpack Compose)       Python Backend (FastAPI)
+----------------------------------+       +--------------------------+
| PDF selection & upload           | HTTP  | PDF storage & parsing     |
| 5-step setup wizard              |------>| Gemini content extraction |
| Workload scoring & scheduling    |<------| Plan normalization       |
| Study map visualization          |       | SQLite persistence       |
| Today screen & replanning        |       | Extraction caching       |
| Focus Mode & Progress            |       +--------------------------+
| Room persistence                 |                   |
+----------------------------------+                   | API
                                              +--------+---------+
                                              | Google Gemini     |
                                              | (gemini-2.5-flash)|
                                              +------------------+
```

The architecture boundary is an intentional design decision: the backend owns content understanding (what is in the PDFs), and Android owns temporal scheduling (when to study what). The backend returns topic structures with workload estimates but no calendar dates. Android computes the schedule locally, enabling instant replanning without network round-trips.

## Tech Stack

**Android:**
- Kotlin, Jetpack Compose, Material 3
- Room, StateFlow, SharedPreferences, SavedStateHandle
- Min SDK 24, Target SDK 36

**Backend:**
- Python, FastAPI, Pydantic v2
- SQLite, google-genai, pypdf, Uvicorn

**AI:**
- Google Gemini (gemini-2.5-flash) for semantic content extraction and global effort calibration

**Testing:**
- pytest (backend, 40 tests)
- JUnit / Compose (Android, unit and instrumentation)

## Getting Started

### Prerequisites

- Android Studio (Hedgehog or later)
- JDK 17 or later (Android Studio bundled JBR recommended)
- Python 3.11 or later
- A Google Gemini API key

### Backend Setup

```bash
cd backend
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

Create a `backend/.env` file with your configuration:

```text
GEMINI_API_KEY=your_api_key_here
GEMINI_MODEL=gemini-2.5-flash
REN_DATA_DIR=./data
```

Start the backend:

```bash
uvicorn main:app --reload --port 8000
```

### Android Setup

Open the project root in Android Studio. The app module should sync automatically via Gradle. Configure the backend URL in the app's network configuration if running on a device or emulator.

### Running Tests

```bash
# Backend
cd backend
python -m pytest

# Android unit tests
./gradlew testDebugUnitTest

# Compile instrumentation tests
./gradlew compileDebugAndroidTestKotlin

# Debug build
./gradlew assembleDebug
```

## Project Status

The application is a functional prototype with the following implemented flows:

- PDF upload with validation and inline error handling
- Five-step setup wizard
- AI-powered content extraction from lecture PDFs
- Day-by-day study schedule generation with feasibility analysis
- Study map visualization (Material and Tree views)
- Today screen with full task manipulation and impact preview
- Adaptive focus timer and dedicated Focus Mode screen
- Progress tab with weekly charts and consistency tracking
- Graceful plan recalculation when overloaded

The application currently supports a single active plan at a time. Multi-course support is planned for future development.

## Design and Evaluation

Ren was developed through a full user-centered design lifecycle:

- 30 semi-structured needfinding interviews identifying five core student challenges
- A questionnaire with 124 respondents validating feature priorities
- Four rounds of evolutionary prototyping with think-aloud user testing (8 participants across 14 sessions)
- Iterative evaluation driving a complete application redesign between iterations 2 and 3

Every feature traces back to evidence from real student interviews and validated questionnaire data.

## Team

Group: Penicillin

- Annafe Artho
- Aryan Ahmed
- Joan Joseph Thomas
- Miah Mariam Akter
- Fathima Shana

## Academic Context

Human-Computer Interaction course project, Sapienza University of Rome. Academic Year 2025/2026.

Supervisor: Prof. Emanuele Panizzi

## License

This project is an academic submission and is not licensed for redistribution.
