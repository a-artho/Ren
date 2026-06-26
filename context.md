# Ren Working Context

Read this first before changing the project. It is a compact map for humans and
AI tools; live code is still authoritative.

## Current product direction

Ren is a time-management study planner for exam preparation.

The core idea:

```text
Upload study material -> extract a content-aware map -> plan it across available
study days -> later support Today, focus sessions, and dynamic replanning.
```

Important framing:

- The app helps manage time; it should not decide what is “skippable” or
  “unimportant” for the student.
- Backend/Gemini may ignore non-study pages such as covers, blank pages, table
  of contents, copyright pages, bibliography-only pages, admin slides, duplicate
  title slides, etc. Those are not study blocks.
- Do not reintroduce priority/skippable/optional topic logic. User-owned states
  are the intended direction: `NotStarted`, `InProgress`, `Completed`,
  `DeferredByUser`, `ExcludedByUser`, `Unscheduled`, `OverCapacity`, etc.
- Preserve lecture/document order and in-document ordering. Scheduling may
  balance workload, but it must not reorder the study path.

## Stack

- Android app: Kotlin, Jetpack Compose, Material 3, single `:app` module
- Package: `com.hci.ren`; min SDK 24; target SDK 36; compile SDK 36.1
- State: `StateFlow` in ViewModels; `rememberSaveable` for top-level routing
- Persistence: Room stores the active study project; `SharedPreferences` stores
  transient generation recovery; setup/upload use `SavedStateHandle`
- Backend: FastAPI, Pydantic, SQLite, `google-genai`, Uvicorn
- Theme: dark Material/Compose UI with custom Ren palette and Inter type;
  dynamic color is off by default

## Architecture boundary

The migration direction is:

```text
Backend = PDF upload + PDF metadata/page anchors + Gemini semantic extraction
Android = workload scoring + scheduling + feasibility + future replanning
```

Backend returns canonical study material:

- source documents
- topics
- ordered blocks
- source refs/page ranges
- workload metadata: duration/effort range, difficulty/density/production scores,
  task type, completion criteria, split allowance, continuity group
- extraction warnings

Backend deliberately does not return calendar dates, day assignments, or a dated
schedule.

Android owns the schedule locally every time Study Plan data is built. This is
important for future Today/focus/replanning work.

## Current app flow

Top-level routes are managed in `MainActivity.kt` without Android Navigation:

```text
Study plan tab
  -> empty landing if no active plan
  -> PDF upload
  -> setup wizard
  -> processing
  -> active study plan

Today tab
  -> placeholder for now; disabled/greyed until a plan exists

Progress tab
  -> placeholder for now; disabled/greyed until a plan exists
```

Current screen constants:

```text
pdf_upload
pdf_setup
plan_processing
study_map_detail
today
progress
```

Bottom navigation has three tabs:

- Study plan
- Today
- Progress

There is no old Home feature in the intended flow. The app is single active plan
for now, not a multi-plan library.

## Setup flow

Setup is a five-step flow:

1. Choose study materials
2. Name the plan
3. Deadline
4. Available daily time
5. Study days

`PlanSetupUiState.canContinue` gates progression. `toSubmission()` is the single
assembly point.

Deadline semantics:

- Deadline date is exclusive for scheduling.
- If an exam is due on 27 June, the plan schedules up to 26 June, not on 27 June.
- `Tomorrow`, `InThreeDays`, `InOneWeek`, and `ChooseDate` are supported.

Daily time is used for initial schedule capacity. Later Today/focus features may
separate available time from actual focus target.

## Plan generation and backend contract

Backend endpoints:

| Method | Endpoint | Meaning |
|---|---|---|
| `POST` | `/documents` | multipart PDF plus optional `requestId`; returns `documentId` |
| `POST` | `/plans` | `{documentIds, requestId, setup}`; returns `planId` |
| `POST` | `/plans/{id}/cancel` | idempotently cancels an active plan |
| `GET` | `/plans/{id}/status` | returns status and internal error field |
| `GET` | `/plans/{id}` | returns completed canonical plan data |

Important backend rules:

- `documentIds` supports 1–10 PDFs.
- Upload limit is 25 MiB; Android and backend limits must stay aligned.
- Documents are sorted by natural filename order before Gemini processing
  (`lecture 2` before `lecture 10`).
- Backend validates PDF content type, `%PDF-` header, request IDs, referenced
  document IDs, model output shape, topic/block ordering, source refs, and
  workload fields.
- Backend returns `extractionWarnings`; Android parses and persists them.
- Terminal statuses are `COMPLETED`, `FAILED`, `CANCELED`.
- Terminal jobs clean up uploaded PDFs.
- Pending jobs resume at backend startup.

Runtime config values must never be committed:

```text
GEMINI_API_KEY
GEMINI_MODEL
REN_DATA_DIR
```

Local backend secrets/state are ignored by Git.

## Android scheduling model

Canonical generated blocks are stored unsplit.

`StudyPlanNormalizer.prepareForLocalScheduling()` derives schedulable chunks from
the canonical blocks using the current daily capacity. This must stay derived,
not permanently persisted, so future changes to daily time can re-split cleanly.

Core scheduling files:

- `feature/studymap/StudyMapModels.kt`
- `feature/studymap/StudyScheduleCalculator.kt`
- `feature/studymap/StudyScheduleModels.kt`
- `feature/studymap/StudyPlanNormalizer.kt`
- `feature/studymap/PlanRealism.kt`
- `feature/plangeneration/StudyWorkload.kt`
- `feature/plangeneration/StudyWorkloadBudget.kt`
- `feature/plangeneration/StudyPlanFeasibilityChecker.kt`

Scheduling invariants:

- Preserve block order.
- Preserve dependencies.
- Preserve continuity groups where possible.
- Use selected study days only.
- Treat deadline as exclusive.
- Balance by robust minutes and cognitive load, not just raw task count.
- Locked/completed tasks have special handling and should not be casually
  rewritten.
- `DeferredByUser` and `ExcludedByUser` do not count toward required workload.
- Unplaced required work must surface as `Unscheduled` or `OverCapacity`; it
  must not disappear.

Workload/realism:

- `requiredStudyMinutes()` applies a buffer for planning realism.
- `PlanRealismCalculator` compares required work with available capacity and
  unscheduled work.
- `StudyScheduleCalculator` returns `StudyScheduleDay` plus `unscheduledTasks`.
- Dynamic splitting happens before local scheduling, not before Room persistence.

## Persistence

`StudyProjectRepository` owns the active project Room row.

Stored project data includes:

- title
- canonical generated plan
- normalized setup/deadline
- timestamps
- daily-time override
- accepted tight-plan flag

It deliberately does not store the uploaded PDFs or source content URI.

Room schema files under `app/schemas/...` should be committed when the DB version
changes.

The Room database is excluded from Android backup:

```text
ren-study-projects.db
ren-study-projects.db-shm
ren-study-projects.db-wal
```

## UI principles

- Prefer Material 3/Compose components and surfaces unless custom UI is genuinely
  needed.
- Avoid patchy per-device layout hacks; fix shared layout primitives instead.
- Use `MaterialTheme`, `RenTheme`, and `RenMotion`; do not create parallel design
  systems casually.
- User-facing strings belong in `app/src/main/res/values/strings.xml`.
- Keep dark mode/OLED-friendly backgrounds consistent.
- Maintain accessible semantics, content descriptions, touch targets, font
  scaling, RTL, and reduced motion.
- The processing page should communicate progress clearly without fake “AI magic”
  language.

Current planned UI direction:

- Study plan is the main map.
- Today will become the operational focus/replanning surface.
- Progress will show outcomes/analytics later.

## Change map

| If changing... | Read first | Tests/checks |
|---|---|---|
| Top-level routing/tabs | `MainActivity.kt` | relevant Compose tests, `compileDebugAndroidTestKotlin` |
| PDF upload/preview | `PdfUploadRoute.kt`, `PdfUploadViewModel.kt`, `PdfUploadScreen.kt` | `PdfUploadUiStateTest`, `PdfUploadScreenTest` |
| Setup wizard | `PlanSetupUiState.kt`, `PlanSetupViewModel.kt`, `PlanSetupScreen.kt` | `PlanSetupUiStateTest`, `PlanSetupScreenTest` |
| Backend API client | `PlanApiRepository.kt`, backend `main.py`, `models.py` | backend pytest, Android generation tests |
| Processing/retry/recovery | `PlanGenerationViewModel.kt`, `PlanGenerationModels.kt`, `PlanGenerationScreen.kt` | `PlanGenerationScreenTest`, unit tests |
| Scheduling/realism | `StudyScheduleCalculator.kt`, `StudyPlanNormalizer.kt`, `PlanRealism.kt`, workload files | `StudyMapModelsTest`, feasibility tests |
| Study plan UI | `StudyMapScreen.kt`, `StudyMapDetailRoute.kt`, `StudyMapDetailViewModel.kt` | `StudyMapScreenTest`, visual emulator check |
| Persistence/Room | `StudyProjectRepository.kt`, schema files | `StudyProjectPersistenceTest`, `StudyProjectDatabaseTest` |
| Backend extraction/Gemini | backend `provider.py`, `planner.py`, `pdf_parser.py`, `models.py` | `test_provider.py`, `test_planner.py`, `test_pdf_parser.py` |
| Theme/motion/components | `ui/theme/`, `ui/motion/`, `ui/components/` | screen tests, visual check |
| Build/dependencies | Gradle files and version catalog | unit tests, assemble, lint if relevant |

## Verification

Use Android Studio bundled JBR as `JAVA_HOME` if the shell JDK is incompatible:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

Common checks:

```powershell
# Android unit tests
.\gradlew.bat testDebugUnitTest

# compile Android/Compose instrumentation tests without a device
.\gradlew.bat compileDebugAndroidTestKotlin

# debug APK build
.\gradlew.bat assembleDebug

# backend tests, run from backend/ to avoid Python package shadowing
cd backend
.\.venv\Scripts\python.exe -m pytest
```

Most recent broad checks that passed locally:

```text
backend pytest: 40 passed
Android: testDebugUnitTest passed
Android: compileDebugAndroidTestKotlin passed
Android: assembleDebug passed
```

Never claim a check passed unless it ran successfully in the current session.

## Git and local files

Do not commit or push unless explicitly asked.

Ignored local/sensitive/generated files include:

- `backend/.env`
- `backend/data/`
- `backend/.venv/`
- `backend/*.log`
- `backend/*.zip`
- `context.local.md`
- `chatgpt/`
- `.codex-logs/`
- `local.properties`
- `.idea/`
- `.gradle/`
- `build/`
- `app/build/`
- Python caches and pytest caches

Before pushing, inspect:

```powershell
git status --short
git ls-files --others --exclude-standard
```

Expected untracked project files after the scheduling/backend migration include
new source files, new backend parser/planner tests, and Room schema `2.json`.

## Safe working procedure

1. Check `git status --short --branch`.
2. Read the relevant section of this file and the code/tests listed in Change map.
3. Confirm current behavior in source before editing.
4. Add/update focused tests for behavior changes where practical.
5. Make the smallest coherent change.
6. Run focused verification, then broader checks proportional to risk.
7. Review diff/status for secrets, generated files, and scope drift.
8. Do not push or commit unless the user explicitly says to.

Keep this file compact. Replace stale facts instead of appending history.
