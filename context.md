# Ren Working Context

Read this first when implementing or improving a feature. It is a compact map for
humans and AI tools; it intentionally does not repeat every rule in `AGENTS.md`.

## How to use this file

1. Read **Core invariants** and the row for your task in **Change map**.
2. Open only the listed production files and their matching tests.
3. Confirm current behavior in code before editing; this file is an index, not a
   substitute for source.
4. Run the narrowest relevant check from **Verification**.

For detailed coding, accessibility, Git, and dependency rules, read `AGENTS.md`.
When the two disagree, live code/configuration is authoritative, then `AGENTS.md`.

## Product and stack

Ren turns a PDF plus study preferences into an AI-generated study plan.

- Android app: Kotlin, Jetpack Compose, Material 3, single `:app` module
- Package: `com.hci.ren`; min SDK 24; target SDK 36; compile SDK 36.1
- State: `StateFlow` in ViewModels; `rememberSaveable` for app routing
- Persistence: `SavedStateHandle` for upload/setup, `SharedPreferences` for
  transient generation recovery, and Room for durable Study Map projects/tasks
- Backend: FastAPI, Pydantic, SQLite, `google-genai`, Uvicorn
- Build: Gradle Kotlin DSL, version catalog, Java 11 source/target, Java 21 daemon
- Theme: custom Ren palette and Inter type; dynamic color is off by default

## End-to-end flow

```text
Home
  -> Android document picker
  -> PDF metadata + local page rendering
  -> 4-step setup (goal, deadline, daily time, study days)
  -> POST PDF /documents
  -> POST setup /plans
  -> poll /plans/{id}/status
  -> GET /plans/{id}
  -> deterministic schedule + realism calculations
  -> persist generated project in Room
  -> Study Map detail (Schedule or Topics)
```

`MainActivity.kt` owns the six-screen state machine:

```text
home -> pdf_upload -> pdf_setup -> plan_processing -> study_map_detail
  \-> study_map_library -> study_map_detail
```

Navigation uses `rememberSaveable`, `AnimatedContent`, and `RenMotion`. It does
not use Android Navigation. Starting a new upload resets upload/setup sessions.
Leaving active generation calls `PlanGenerationViewModel.reset()`, which clears
local recovery state and attempts backend cancellation.

## Core invariants

- Preserve the established `Route -> Screen -> ViewModel -> immutable UiState`
  shape. Screen composables receive state/events; external I/O belongs in a
  repository and runs off the main thread.
- Preserve unrelated work: inspect `git status --short --branch` before edits.
- Reuse `MaterialTheme`, `RenTheme`, and `RenMotion`; do not introduce a parallel
  palette, type system, animation duration, or navigation mechanism.
- User-facing strings belong in `app/src/main/res/values/strings.xml`.
- Maintain accessible semantics, meaningful content descriptions, test tags,
  adequate touch targets, dark theme, RTL, font scaling, and reduced motion.
- A PDF must remain available through its persisted content URI until upload.
- Android and backend upload limits must stay aligned at 25 MiB.
- Generation retries rely on idempotent request IDs. Do not casually regenerate,
  omit, or reinterpret `requestId` across upload/create retry paths.
- Backend terminal states are `COMPLETED`, `FAILED`, and `CANCELED`; pending jobs
  resume at backend startup. Terminal jobs clean up their uploaded PDF.
- Never expose backend exception text, API keys, raw internal errors, or document
  contents in the Android UI or committed documentation.

## Change map

| If changing... | Read first | Tests |
|---|---|---|
| App routing, session reset, system back | `MainActivity.kt`; each destination's Route/Screen | affected Compose screen tests |
| Home dashboard/actions/material sheet | `feature/home/presentation/` and `components/` | `HomeUiStateTest`, `HomeScreenTest` |
| PDF selection, metadata, page cache | `PdfUploadRoute.kt`, `PdfUploadViewModel.kt`, `PdfUploadUiState.kt` | `PdfUploadUiStateTest`, `PdfUploadScreenTest` |
| PDF rendering or URI access | `PdfDocumentRepository.kt`, then upload ViewModel | `PdfUploadUiStateTest`; compile UI tests |
| Setup questions/validation/persistence | `PlanSetupUiState.kt`, `PlanSetupViewModel.kt`, `PlanSetupScreen.kt` | `PlanSetupUiStateTest`, `PlanSetupScreenTest` |
| Upload/create/poll/cancel HTTP contract | Android `PlanApiRepository.kt`; backend `main.py`, `models.py` | `PlanGenerationModelsTest`; `test_api.py` |
| Generation recovery/retry/progress | `PlanGenerationViewModel.kt`, `PlanGenerationModels.kt` | `PlanGenerationModelsTest`, `PlanGenerationScreenTest` |
| Study Map library/persistence | `studymap/StudyProjectRepository.kt`, `StudyMapLibrary*` | `StudyProjectPersistenceTest`, `StudyProjectDatabaseTest`, `StudyMapLibraryScreenTest` |
| Study Map schedule/topics/task actions | `studymap/StudyMapScreen.kt`, `StudyMapDetailViewModel.kt`, `StudyMapModels.kt` | `StudyMapModelsTest`, `StudyMapScreenTest` |
| Realism and in-place plan adjustments | `studymap/StudyMapModels.kt`, generation ViewModel | `StudyMapModelsTest`; compile Compose tests |
| Legacy feasibility/adaptation helpers | `StudyPlanFeasibilityChecker.kt`, `RealityCheckScreen.kt`, generation ViewModel/models | `StudyPlanFeasibilityCheckerTest`; compile Compose tests |
| Generated-plan validation | backend `models.py`, `provider.py`; Android generation models | `test_models.py`, `test_provider.py` |
| Job lifecycle, cleanup, restart | backend `main.py`, `store.py` | `test_processing.py`, `test_store.py`, `test_api.py` |
| Colors/type/dark theme | `ui/theme/Color.kt`, `Theme.kt`, `Type.kt` | affected screen tests; visual check |
| Animation/transitions | `ui/motion/RenMotion.kt`, calling screen | affected screen tests; reduced-motion check |
| Dependencies/build/lint | `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties` | unit tests, lint, assemble |

Paths under `feature/` above are relative to
`app/src/main/java/com/hci/ren/feature/`. Android tests mirror package paths in
`app/src/test/` and `app/src/androidTest/`.

## Feature boundaries

### Home

`HomeRoute` owns screen state and dispatches `HomeAction`; `HomeScreen` selects
empty or active content. Reusable dashboard UI lives under `components/`.
Some home actions intentionally show “not available” feedback; do not silently
make placeholder actions appear functional.

The shared product navigation currently presents Home, Study Map, and Insights.
The Study Map item opens the persisted project library. Selecting a project opens
its existing Schedule/Topics detail destination. Insights remains an unavailable
placeholder until its feature is implemented.

### PDF upload and preview

`PdfUploadViewModel` restores the selected URI/page with `SavedStateHandle`,
rejects files over 25 MiB, bounds stale async results by session/generation, and
limits rendering concurrency to two jobs. `PdfDocumentRepository` takes a
persistable read grant and uses Android `PdfRenderer`.

Rendering safety limits:

- bitmap cache: weighted LRU, 32 MiB
- maximum render dimension: 8,192 px
- maximum rendered pixels: 4,000,000
- preview target width: 1,400 px
- evicted bitmaps must also leave `renderedPages`; otherwise memory is unbounded

### Plan setup

The wizard has four enum-backed steps: goal, deadline, daily time, study days.
`PlanSetupUiState.canContinue` is the validation gate and `toSubmission()` is the
single assembly point. `PlanSetupViewModel` persists answers in `SavedStateHandle`.
Custom date handling deliberately separates UTC date-picker milliseconds from
the user's local “today” validation; preserve the timezone tests.

### Plan generation

`PlanGenerationViewModel` persists submission, request ID, and plan ID so work
can resume after process death. It distinguishes upload/create, polling, and
backend-terminal failures because retry identity differs by phase. Transient
HTTP failures include 408, 429, and 5xx. The repository uses `HttpURLConnection`
with emulator base URL `10.0.2.2:8000` and physical-device loopback
`127.0.0.1:8000`.

Wire status mapping is in `PlanGenerationModels.kt`. Android presents backend
`CANCELED` as a failed/terminal visible state; cancellation normally follows a
user exit and local reset.

After a completed plan is fetched, `PlanGenerationViewModel` saves the complete
project aggregate to Room before `MainActivity` routes directly to Study Map
detail. There is no draft/confirm-plan state. Generation submission/request/plan
IDs remain transient recovery data and are cleared after the Room write succeeds.
Retry after a Room write failure retries persistence without regenerating the plan.

Legacy `StudyPlanFeasibilityChecker`, `StudyPlanAdapter`,
`StudyPlanScopeAdjuster`, and `RealityCheckScreen` remain in the source tree for
compatibility with their existing tests, but normal navigation no longer routes
through the standalone Reality Check or plan-details screens.

### Study Map

`StudyProjectRepository` is the device-local source of truth. One Room row owns
the complete generated plan, tasks, normalized setup/deadline, title, timestamps,
daily-time override, and tight-plan acceptance. It deliberately stores neither
the PDF nor its content URI. Deleting a row atomically removes its embedded tasks;
the backend generation record is not part of library CRUD.

`StudyMapLibraryViewModel` observes all projects and applies forgiving title
search, All/Active/Behind/Completed/Plan Issue filters, and the five supported
sort orders. `StudyMapDetailViewModel` loads a selected project and serializes
task/deadline/scope updates back to Room. Home opens the library; successful
generation still opens detail directly; detail back returns to the library.

`feature/studymap/StudyMapModels.kt` contains pure source-of-truth calculations:

- `PlanRealismCalculator`: remaining required minutes versus capacity through
  the deadline; statuses are on-track, tight, unrealistic, or no-deadline
- `StudyScheduleCalculator`: schedules remaining required tasks only on selected
  study days, preserves completed work, and exposes work that does not fit as
  unscheduled or over-capacity
- `PlanAdjustmentService`: calculates the earliest buffered deadline and applies
  recoverable scope reductions or practice-duration reductions
- `TaskProgressCalculator`: project/topic completion from non-excluded tasks

Daily totals are derived from each day's visible tasks, never a cached total.
Optional tasks do not count toward required time and remain recoverable. Locked
tasks cannot start while dependencies are unresolved. Unknown task types map to
the safe Custom presentation.

`StudyMapScreen.kt` uses the existing Ren Material theme and provides:

- project summary, progress, deadline, total estimate, available time, realism
- in-page Schedule/Topics switcher
- next task, expandable day/topic sections, unscheduled work, completion state
- task detail sheet with start, complete, duration, skip, optional, and restore
- in-place Reduce scope, Increase daily time, Extend deadline, and Continue
  anyway adjustments
- sticky context-sensitive CTA above Home/Study Map/Insights navigation

Normal plan adjustments stay inside Study Map, recalculate immediately, and update
the durable project's `updatedAt`. Start currently changes task status to
in-progress; there is no timer/focus destination to launch yet. Insights remains
unimplemented.

## Backend contract

| Method | Endpoint | Meaning |
|---|---|---|
| `POST` | `/documents` | multipart PDF plus optional `requestId`; returns `documentId` |
| `POST` | `/plans` | `{documentId, requestId, setup}`; returns `planId` |
| `POST` | `/plans/{id}/cancel` | idempotently cancels an active plan |
| `GET` | `/plans/{id}/status` | returns status and internal error field |
| `GET` | `/plans/{id}` | returns completed topics, blocks, and total minutes |

The backend validates content type, `%PDF-` header, size, referenced IDs,
contiguous topic/block ordering, and that estimated durations meet task-type
minimums. Generated blocks include task type, priority, priority rationale,
minimum useful minutes, and emergency skip eligibility. SQLite stores documents and plan jobs. Both
document upload and plan creation are idempotent by request ID. Processing tries
the AI provider twice, persists status changes, and removes the PDF after any
terminal outcome. Startup removes old unclaimed uploads and resumes pending jobs.

Gemini structured output uses the simple dict `GEMINI_PLAN_SCHEMA`; returned JSON
is then validated with Pydantic `GeneratedPlan`. Do not replace it with a full
Pydantic-generated schema without testing against the installed Gemini SDK.

Runtime configuration names may be documented, but values must not be committed:
`GEMINI_API_KEY`, optional `GEMINI_MODEL`, and optional `REN_DATA_DIR`.

## UI system

- Theme entry: `RenTheme(dynamicColor = false)`
- Semantic colors: prefer `MaterialTheme.colorScheme`; source palette is Ren
  Green/Sage/Taupe in `Color.kt`
- Typography: Inter roles in `Type.kt`
- Motion: `RenMotionDurationMillis = 250`, `RenMotionEasing`, and
  `renScreenTransform(forward, reducedMotion)`
- Edge-to-edge is enabled; screens must account for system bars
- Loading, empty, error, populated, dark-theme, constrained-size, and font-scale
  states are part of UI correctness

## Verification

Use Android Studio's bundled JBR as `JAVA_HOME` when the shell JDK is incompatible.

```powershell
# focused Android unit test
.\gradlew.bat testDebugUnitTest --tests "*TestClass"

# all local Android logic
.\gradlew.bat testDebugUnitTest

# compile Compose/instrumented tests (no device required)
.\gradlew.bat compileDebugAndroidTestKotlin

# source/resource/build checks
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug

# backend (run from backend/ to avoid Python package shadowing)
.\.venv\Scripts\python.exe -m pytest tests -q
```

Run `connectedDebugAndroidTest` only with an emulator/device. For a physical
device talking to the local backend, first run `adb reverse tcp:8000 tcp:8000`.
If Gradle hits a Windows file lock around generated resources, run
`.\gradlew.bat --stop` once, then retry the failed command.

Verification scope:

- Documentation only: inspect file and Git diff/status
- Pure model/validation logic: focused unit test, then full unit suite
- Compose behavior/semantics: relevant UI test plus Android-test compilation
- Resource/manifest/build change: lint plus assemble
- Backend contract/lifecycle: relevant pytest file, then all backend tests
- Cross-stack contract: Android and backend suites

Never claim a check passed unless it ran successfully in the current session.

## Safe working procedure

1. `git status --short --branch`; treat existing changes as user-owned.
2. Read the relevant row in **Change map**, implementation, and nearby tests.
3. For behavior changes, add a focused regression test where practical.
4. Make the smallest coherent change; avoid unrelated refactors/upgrades.
5. Run focused verification, then the broader check justified by risk.
6. Review `git diff` and status for generated files, secrets, and scope drift.
7. Do not commit, push, branch, or open a PR unless explicitly requested.

Never edit or commit generated/machine data: `.gradle/`, `.kotlin/`, `.idea/`,
`build/`, `app/build/`, `local.properties`, `backend/data/`, `__pycache__/`, or
`.env`. `AGENTS.md` is intentionally local; do not stage it.

## Keeping this context useful

Update `context.md` in the same change when a feature boundary, navigation flow,
API endpoint/payload, persistence mechanism, invariant, or verification command
changes. Keep it compact: replace stale facts instead of appending history.
Do not add secrets, personal paths, branch-specific state, line numbers, exhaustive
symbol lists, transient test counts, or general advice already in `AGENTS.md`.
