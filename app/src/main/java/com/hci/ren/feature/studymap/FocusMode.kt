package com.hci.ren.feature.studymap

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.MobileOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.hci.ren.R
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.StudySourceDocument
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.likelyStudyMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

data class FocusDayState(
    val date: String,
    val focusMinutes: Int = InitialFocusMinutes,
    val breakMinutes: Int = InitialBreakMinutes,
    val focusedSeconds: Int = 0,
    val breakSeconds: Int = 0,
    val completedRounds: Int = 0,
    val completedLeaves: Int = 0,
    val interruptionCount: Int = 0,
    val awaySeconds: Int = 0,
    val pausedSeconds: Int = 0,
    val quietSeconds: Int = 0,
    val screenInteractionCount: Int = 0,
    val phoneDownSeconds: Int = 0,
    val faceDownAssistEnabled: Boolean = false,
    val cleanStreak: Int = 0,
    val interruptedStreak: Int = 0,
    val recoveryStartedAtRealtimeMillis: Long = 0L,
    val lastRound: FocusDayRoundSummary? = null,
)

data class FocusDayRoundSummary(
    val completed: Boolean,
    val focusedSeconds: Int,
    val flowOvertimeSeconds: Int = 0,
    val quietSeconds: Int,
    val screenInteractionCount: Int,
    val phoneDownSeconds: Int,
    val faceDownAssistEnabled: Boolean,
    val softGlanceCount: Int,
    val interruptionCount: Int,
    val awaySeconds: Int,
    val pausedSeconds: Int,
    val suggestion: String,
)

@Composable
fun AdaptiveFocusMode(
    project: StudyProject,
    taskId: String,
    session: TodaySessionState?,
    onDismiss: () -> Unit,
    onMarkDone: (date: String, taskId: String) -> Unit,
    onOpenTask: (date: String, taskId: String, pulledIntoToday: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    startAutomatically: Boolean = false,
    focusDayState: FocusDayState? = null,
    onFocusDayStateChange: (FocusDayState) -> Unit = {},
    onFocusSessionRecorded: (date: String, record: FocusSessionRecord) -> Unit = { _, _ -> },
) {
    val data = remember(project) {
        buildStudyMapData(
            plan = project.plan,
            preferences = project.preferences,
            dailyMinutesOverride = project.dailyMinutesOverride,
            dailyAvailableMinutesByDate = project.dailyAvailableMinutesByDate,
            taskStateById = project.taskStateById,
        )
    }
    var clockNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(project.preferences.studyDayResetOffsetHours) {
        while (true) {
            clockNowMillis = System.currentTimeMillis()
            delay(60_000L)
        }
    }
    val today = currentStudyCalendar(project.preferences, clockNowMillis).toStudyDate()
    val suppliedDayState = focusDayState?.takeIf { it.date == today }
    val todaySession = session?.takeIf { it.date == today }
    val baseAvailableMinutes = effectiveAvailableMinutesForStudyDate(
        date = today,
        requestedMinutes = todayBaseAvailableMinutes(project, data, today),
        resetOffsetHours = project.preferences.studyDayResetOffsetHours,
        nowMillis = clockNowMillis,
    )
    val availableMinutes = effectiveAvailableMinutesForStudyDate(
        date = today,
        requestedMinutes = todaySession?.availableMinutes ?: baseAvailableMinutes,
        resetOffsetHours = project.preferences.studyDayResetOffsetHours,
        nowMillis = clockNowMillis,
    )
    val hasAvailabilityOverride = todaySession?.availableMinutes != null && availableMinutes != baseAvailableMinutes
    val todayPlan = remember(data, today, availableMinutes, todaySession, hasAvailabilityOverride) {
        TodaySessionPlanner().plan(
            data = data,
            date = today,
            availableMinutes = availableMinutes,
            session = todaySession,
            hasAvailabilityOverride = hasAvailabilityOverride,
        )
    }
    val breakImpact = remember(todayPlan) {
        FocusBreakImpact(
            remainingBufferMinutes = todayPlan.remainingMinutes,
            alreadyOverMinutes = todayPlan.overPlannedMinutes,
            waitingTaskCount = todayPlan.wontFitTodayTasks.size,
            waitingMinutes = todayPlan.overflowMinutes,
        )
    }
    val doneTodayIds = remember(todayPlan) { todayPlan.doneTodayTasks.mapTo(mutableSetOf()) { it.id } }
    val pullInCandidateIds = remember(todayPlan) { todayPlan.pullInCandidates.mapTo(mutableSetOf()) { it.id } }
    val focusQueue = remember(todayPlan) {
        (todayPlan.doTodayTasks + todayPlan.pulledInTasks + todayPlan.pullInCandidates)
            .distinctBy { it.id }
            .filterNot { it.status == StudyTaskStatus.Completed || it.id in doneTodayIds }
    }
    val currentTask = remember(taskId, data, focusQueue) {
        focusQueue.firstOrNull { it.id == taskId }
            ?: data.activeTasks.firstOrNull { it.id == taskId }
    }

    if (currentTask == null) {
        FocusEmptyState(onDismiss = onDismiss, modifier = modifier)
        return
    }

    val previousFocusSecondsForTask = remember(todaySession, currentTask.id) {
        todaySession?.focusSessions
            ?.filter { it.taskId == currentTask.id }
            ?.sumOf { it.focusSeconds }
            ?: 0
    }
    val initialFocusMinutes = remember(currentTask.id) { initialFocusMinutes(currentTask) }
    val currentIndex = focusQueue.indexOfFirst { it.id == currentTask.id }
    val nextTask = remember(focusQueue, currentTask) {
        if (currentIndex >= 0) {
            focusQueue.drop(currentIndex + 1).firstOrNull()
        } else {
            focusQueue.firstOrNull { it.id != currentTask.id }
        }
    }
    val initialDayState = remember(today, suppliedDayState, initialFocusMinutes) {
        suppliedDayState ?: FocusDayState(
            date = today,
            focusMinutes = initialFocusMinutes,
            breakMinutes = InitialBreakMinutes,
        )
    }
    var focusMinutes by rememberSaveable(today) {
        mutableIntStateOf(initialDayState.focusMinutes.coerceIn(MinFocusMinutes, MaxFocusMinutes))
    }
    var suggestedFocusMinutes by rememberSaveable(today) {
        mutableIntStateOf(initialDayState.focusMinutes.coerceIn(MinFocusMinutes, MaxFocusMinutes))
    }
    val focusMinutesManuallyEdited = focusMinutes != suggestedFocusMinutes
    var breakMinutes by rememberSaveable(today) {
        mutableIntStateOf(initialDayState.breakMinutes.coerceIn(MinBreakMinutes, MaxBreakMinutes))
    }
    var phaseName by rememberSaveable(today) { mutableStateOf(FocusPhase.Ready.name) }
    val phase = FocusPhase.valueOf(phaseName)
    var remainingSeconds by rememberSaveable(today) { mutableIntStateOf(focusMinutes * SecondsPerMinute) }
    var roundSerial by rememberSaveable(today) { mutableIntStateOf(0) }
    var focusedSeconds by rememberSaveable(today) { mutableIntStateOf(0) }
    var plannedFocusSeconds by rememberSaveable(today) { mutableIntStateOf(0) }
    var bankedFlowOvertimeSeconds by rememberSaveable(today) { mutableIntStateOf(0) }
    var recordedBreakSeconds by rememberSaveable(today) { mutableIntStateOf(0) }
    var quietSeconds by rememberSaveable(today) { mutableIntStateOf(0) }
    var screenInteractionCount by rememberSaveable(today) { mutableIntStateOf(0) }
    var lastInteractionAtMillis by rememberSaveable(today) { mutableLongStateOf(0L) }
    var screenSettled by rememberSaveable(today) { mutableStateOf(false) }
    var phoneDownSeconds by rememberSaveable(today) { mutableIntStateOf(0) }
    var pauseCount by rememberSaveable(today) { mutableIntStateOf(0) }
    var pauseStartedAtMillis by rememberSaveable(today) { mutableLongStateOf(0L) }
    var pausedMillis by rememberSaveable(today) { mutableLongStateOf(0L) }
    var screenUpPauseInterruptionCounted by rememberSaveable(today) { mutableStateOf(false) }
    var backgroundInterruptions by rememberSaveable(today) { mutableIntStateOf(0) }
    var softGlanceCount by rememberSaveable(today) { mutableIntStateOf(0) }
    var backgroundStartedAtMillis by rememberSaveable(today) { mutableLongStateOf(0L) }
    var backgroundAwayMillis by rememberSaveable(today) { mutableLongStateOf(0L) }
    var dayFocusedSeconds by rememberSaveable(today) { mutableIntStateOf(initialDayState.focusedSeconds) }
    var dayBreakSeconds by rememberSaveable(today) { mutableIntStateOf(initialDayState.breakSeconds) }
    var dayCompletedRounds by rememberSaveable(today) { mutableIntStateOf(initialDayState.completedRounds) }
    var dayInterruptionCount by rememberSaveable(today) { mutableIntStateOf(initialDayState.interruptionCount) }
    var dayAwaySeconds by rememberSaveable(today) { mutableIntStateOf(initialDayState.awaySeconds) }
    var dayPausedSeconds by rememberSaveable(today) { mutableIntStateOf(initialDayState.pausedSeconds) }
    var dayQuietSeconds by rememberSaveable(today) { mutableIntStateOf(initialDayState.quietSeconds) }
    var dayScreenInteractionCount by rememberSaveable(today) {
        mutableIntStateOf(initialDayState.screenInteractionCount)
    }
    var dayPhoneDownSeconds by rememberSaveable(today) { mutableIntStateOf(initialDayState.phoneDownSeconds) }
    var faceDownAssistEnabled by rememberSaveable(today) { mutableStateOf(initialDayState.faceDownAssistEnabled) }
    var cleanStreak by rememberSaveable(today) { mutableIntStateOf(initialDayState.cleanStreak) }
    var interruptedStreak by rememberSaveable(today) { mutableIntStateOf(initialDayState.interruptedStreak) }
    var completedLeavesThisSession by rememberSaveable(today) { mutableIntStateOf(initialDayState.completedLeaves) }
    var autoStarted by rememberSaveable(currentTask.id) { mutableStateOf(false) }
    var leafDoneChoicePending by rememberSaveable(today) { mutableStateOf(false) }
    var deepFocusMinutes by rememberSaveable(today) { mutableStateOf<Int?>(null) }
    var pendingFaceDownStartMinutes by rememberSaveable(today) { mutableStateOf<Int?>(null) }
    var pausedFromFlowOvertime by rememberSaveable(today) { mutableStateOf(false) }
    var recoveryStartedAtMillis by rememberSaveable(today) {
        mutableLongStateOf(initialDayState.recoveryStartedAtRealtimeMillis)
    }
    var lastReport by remember(today) { mutableStateOf(initialDayState.lastRound?.toRoundReport()) }
    var notificationPermissionGranted by rememberNotificationPermissionState()
    var notificationEvent by remember { mutableStateOf<FocusNotificationEvent?>(null) }
    var completionCueEvent by remember { mutableStateOf<FocusCompletionCueEvent?>(null) }
    var focusModeNotice by remember { mutableStateOf<String?>(null) }
    var focusModeNoticeSerial by remember { mutableIntStateOf(0) }
    val posture = rememberFocusPostureReading(
        enabled = faceDownAssistEnabled &&
            (phase in FocusTrackingPhases || phase in FocusStartGatePhases || pendingFaceDownStartMinutes != null),
    )
    val latestPhoneDown by rememberUpdatedState(posture.isPhoneDown)
    val focusView = LocalView.current

    fun setPhase(next: FocusPhase) {
        phaseName = next.name
        recoveryStartedAtMillis = if (
            next in RecoveryPhases &&
            (dayFocusedSeconds > 0 || focusedSeconds > 0 || lastReport != null)
        ) {
            SystemClock.elapsedRealtime()
        } else {
            0L
        }
    }

    fun currentDayState(
        recoveryStartedAt: Long = recoveryStartedAtMillis,
        lastRound: FocusRoundReport? = lastReport,
    ): FocusDayState = FocusDayState(
        date = today,
        focusMinutes = focusMinutes,
        breakMinutes = breakMinutes,
        focusedSeconds = dayFocusedSeconds,
        breakSeconds = dayBreakSeconds,
        completedRounds = dayCompletedRounds,
        completedLeaves = completedLeavesThisSession,
        interruptionCount = dayInterruptionCount,
        awaySeconds = dayAwaySeconds,
        pausedSeconds = dayPausedSeconds,
        quietSeconds = dayQuietSeconds,
        screenInteractionCount = dayScreenInteractionCount,
        phoneDownSeconds = dayPhoneDownSeconds,
        faceDownAssistEnabled = faceDownAssistEnabled,
        cleanStreak = cleanStreak,
        interruptedStreak = interruptedStreak,
        recoveryStartedAtRealtimeMillis = recoveryStartedAt,
        lastRound = lastRound?.toDaySummary(),
    )

    fun publishDayState(
        recoveryStartedAt: Long = recoveryStartedAtMillis,
        lastRound: FocusRoundReport? = lastReport,
    ) {
        onFocusDayStateChange(currentDayState(recoveryStartedAt, lastRound))
    }

    fun currentRoundInterruptionCount(): Int = backgroundInterruptions + pauseCount

    fun currentFlowOvertimeSeconds(): Int =
        bankedFlowOvertimeSeconds + if (
            phase == FocusPhase.FlowOvertime ||
            (phase == FocusPhase.Paused && pausedFromFlowOvertime)
        ) {
            (-remainingSeconds).coerceAtLeast(0)
        } else {
            0
        }

    fun currentBreakElapsedSeconds(): Int =
        (breakMinutes * SecondsPerMinute - remainingSeconds).coerceAtLeast(0)

    fun currentUnrecordedBreakSeconds(): Int =
        (currentBreakElapsedSeconds() - recordedBreakSeconds).coerceAtLeast(0)

    fun currentPausedMillis(): Long =
        pausedMillis + if (pauseStartedAtMillis == 0L) {
            0L
        } else {
            SystemClock.elapsedRealtime() - pauseStartedAtMillis
        }

    fun countScreenUpPauseInterruptionIfNeeded(now: Long = SystemClock.elapsedRealtime()) {
        if (
            phase == FocusPhase.Paused &&
            pauseStartedAtMillis != 0L &&
            !screenUpPauseInterruptionCounted &&
            now - pauseStartedAtMillis >= InterruptionGraceMillis
        ) {
            pauseCount += 1
            screenUpPauseInterruptionCounted = true
        }
    }

    fun currentLeafFocusSeconds(): Int = previousFocusSecondsForTask + focusedSeconds

    fun suggestedFlowExtensionMinutes(): Int = flowExtensionMinutes(
        currentFocusMinutes = focusMinutes,
        flowOvertimeSeconds = currentFlowOvertimeSeconds(),
        leafFocusedSeconds = currentLeafFocusSeconds(),
        leafLikelyMinutes = currentTask.likelyStudyMinutes,
        remainingBufferMinutes = breakImpact.remainingBufferMinutes,
        interruptionCount = currentRoundInterruptionCount(),
        sessionFocusedSeconds = dayFocusedSeconds + focusedSeconds,
    )

    fun recordFocusSession(
        outcome: FocusSessionOutcome,
        focusSecondsValue: Int,
        plannedFocusSecondsValue: Int = plannedFocusSeconds,
        flowOvertimeSecondsValue: Int = 0,
        breakSecondsValue: Int,
        awaySecondsValue: Int = 0,
        interruptionCountValue: Int = 0,
    ) {
        val normalizedFocusSeconds = focusSecondsValue.coerceAtLeast(0)
        val normalizedPlannedFocusSeconds = plannedFocusSecondsValue.coerceAtLeast(0)
        val record = FocusSessionRecord(
            taskId = currentTask.id,
            plannedFocusMinutes = normalizedPlannedFocusSeconds.toCeilMinutes(),
            plannedFocusSeconds = normalizedPlannedFocusSeconds,
            plannedBreakMinutes = breakMinutes,
            focusSeconds = normalizedFocusSeconds,
            flowOvertimeSeconds = flowOvertimeSecondsValue.coerceIn(
                0,
                normalizedFocusSeconds,
            ),
            breakSeconds = breakSecondsValue.coerceAtLeast(0),
            awaySeconds = awaySecondsValue.coerceAtLeast(0),
            interruptionCount = interruptionCountValue.coerceAtLeast(0),
            outcome = outcome,
            endedAtMillis = System.currentTimeMillis(),
        )
        if (record.hasTrackedTime) {
            onFocusSessionRecorded(today, record)
        }
    }

    fun recordBackgroundAway(awayMillis: Long, resetOnLongGap: Boolean): Boolean {
        if (awayMillis < InterruptionGraceMillis) return false
        if (awayMillis >= LongMomentumResetMillis && resetOnLongGap) {
            dayAwaySeconds += (awayMillis / 1_000L).toInt()
            dayInterruptionCount += 1
            return true
        }
        if (awayMillis < SoftGlanceMaxMillis) {
            softGlanceCount += 1
        } else {
            backgroundInterruptions += 1
        }
        backgroundAwayMillis += awayMillis
        return false
    }

    fun finalizeBackgroundAway(resetOnLongGap: Boolean = true): Boolean {
        val startedAt = backgroundStartedAtMillis
        if (startedAt == 0L) return false

        backgroundStartedAtMillis = 0L
        val awayMillis = SystemClock.elapsedRealtime() - startedAt
        return recordBackgroundAway(
            awayMillis = awayMillis,
            resetOnLongGap = resetOnLongGap,
        )
    }

    fun resetRoundMetrics() {
        focusedSeconds = 0
        plannedFocusSeconds = 0
        bankedFlowOvertimeSeconds = 0
        quietSeconds = 0
        screenInteractionCount = 0
        lastInteractionAtMillis = SystemClock.elapsedRealtime()
        screenSettled = false
        phoneDownSeconds = 0
        pauseCount = 0
        pauseStartedAtMillis = 0L
        pausedMillis = 0L
        screenUpPauseInterruptionCounted = false
        pausedFromFlowOvertime = false
        backgroundInterruptions = 0
        softGlanceCount = 0
        backgroundStartedAtMillis = 0L
        backgroundAwayMillis = 0L
    }

    fun resetMomentumRhythm(reason: String? = null) {
        leafDoneChoicePending = false
        deepFocusMinutes = null
        focusMinutes = InitialFocusMinutes
        suggestedFocusMinutes = InitialFocusMinutes
        breakMinutes = InitialBreakMinutes
        cleanStreak = 0
        interruptedStreak = 0
        if (reason != null) {
            lastReport = FocusRoundReport(
                id = SystemClock.elapsedRealtime(),
                completed = false,
                focusedSeconds = 0,
                quietSeconds = 0,
                screenInteractionCount = 0,
                phoneDownSeconds = 0,
                faceDownAssistEnabled = faceDownAssistEnabled,
                softGlanceCount = 0,
                interruptionCount = 0,
                awaySeconds = 0,
                pauseSeconds = 0,
                suggestion = reason,
            )
        }
        resetRoundMetrics()
        remainingSeconds = InitialFocusMinutes * SecondsPerMinute
        recoveryStartedAtMillis = SystemClock.elapsedRealtime()
        phaseName = FocusPhase.Ready.name
        publishDayState()
    }

    fun nextFocusAfterRecoveryGap(requestedMinutes: Int): Int {
        val recoveryStartedAt = recoveryStartedAtMillis
        if (recoveryStartedAt == 0L) return requestedMinutes

        val awayMillis = SystemClock.elapsedRealtime() - recoveryStartedAt
        recoveryStartedAtMillis = 0L
        if (awayMillis < InterruptionGraceMillis) return requestedMinutes

        dayAwaySeconds += (awayMillis / 1_000L).toInt()
        return when {
            awayMillis >= LongMomentumResetMillis -> {
                resetMomentumRhythm("Long break. Fresh start at 10 minutes; momentum cooled, not vanished.")
                InitialFocusMinutes
            }
            awayMillis >= RhythmBreakSeconds * 1_000L -> {
                cleanStreak = 0
                interruptedStreak += 1
                breakMinutes = (breakMinutes + 1).coerceIn(MinBreakMinutes, MaxBreakMinutes)
                focusStep(requestedMinutes, -1)
            }
            else -> requestedMinutes
        }
    }

    fun startFocusRound(minutes: Int = focusMinutes) {
        pendingFaceDownStartMinutes = null
        leafDoneChoicePending = false
        deepFocusMinutes = null
        val requestedMinutes = nextFocusAfterRecoveryGap(minutes)
        val normalized = requestedMinutes.coerceIn(MinFocusMinutes, MaxFocusMinutes)
        focusMinutes = normalized
        if (normalized != minutes) {
            suggestedFocusMinutes = normalized
        }
        resetRoundMetrics()
        plannedFocusSeconds = normalized * SecondsPerMinute
        remainingSeconds = plannedFocusSeconds
        lastInteractionAtMillis = SystemClock.elapsedRealtime()
        setPhase(FocusPhase.Focus)
        performFocusStartHaptic(focusView)
        roundSerial += 1
        publishDayState()
    }

    fun pauseFocusForScreenUp() {
        if (phase !in FocusTrackingPhases || !faceDownAssistEnabled || !posture.isSupported) return
        pendingFaceDownStartMinutes = focusMinutes
        pausedFromFlowOvertime = phase == FocusPhase.FlowOvertime
        if (pauseStartedAtMillis == 0L) {
            pauseStartedAtMillis = SystemClock.elapsedRealtime()
            screenUpPauseInterruptionCounted = false
        }
        screenSettled = false
        setPhase(FocusPhase.Paused)
        publishDayState()
    }

    fun resumePausedFocus() {
        if (phase != FocusPhase.Paused) return
        countScreenUpPauseInterruptionIfNeeded()
        val startedAt = pauseStartedAtMillis
        if (startedAt != 0L) {
            pausedMillis += SystemClock.elapsedRealtime() - startedAt
            pauseStartedAtMillis = 0L
        }
        screenUpPauseInterruptionCounted = false
        pendingFaceDownStartMinutes = null
        lastInteractionAtMillis = SystemClock.elapsedRealtime()
        screenSettled = false
        setPhase(if (pausedFromFlowOvertime) FocusPhase.FlowOvertime else FocusPhase.Focus)
        roundSerial += 1
        publishDayState()
    }

    fun updateFaceDownAssist(enabled: Boolean) {
        if (enabled == faceDownAssistEnabled) return

        faceDownAssistEnabled = enabled
        if (enabled) {
            focusModeNotice = FocusPhoneDownNotice
            focusModeNoticeSerial += 1
            publishDayState()
            return
        }

        val pendingMinutes = pendingFaceDownStartMinutes
        if (phase == FocusPhase.Paused) {
            resumePausedFocus()
            return
        }
        if (pendingMinutes != null && phase in FocusStartGatePhases) {
            startFocusRound(pendingMinutes)
            return
        }

        pendingFaceDownStartMinutes = null
        publishDayState()
    }

    fun requestFocusStart(minutes: Int = focusMinutes) {
        if (phase == FocusPhase.Paused) {
            if (faceDownAssistEnabled && posture.isSupported && !latestPhoneDown) {
                pendingFaceDownStartMinutes = minutes.coerceIn(MinFocusMinutes, MaxFocusMinutes)
                return
            }
            resumePausedFocus()
            return
        }
        if (faceDownAssistEnabled && posture.isSupported && !latestPhoneDown) {
            pendingFaceDownStartMinutes = minutes.coerceIn(MinFocusMinutes, MaxFocusMinutes)
            return
        }
        startFocusRound(minutes)
    }

    fun startBreak(minutes: Int = breakMinutes) {
        val normalized = minutes.coerceIn(MinBreakMinutes, MaxBreakMinutes)
        breakMinutes = normalized
        recordedBreakSeconds = 0
        remainingSeconds = normalized * SecondsPerMinute
        setPhase(FocusPhase.Break)
        performFocusStartHaptic(focusView)
        roundSerial += 1
        publishDayState()
    }

    fun completeFocusRound(
        completed: Boolean,
        nextPhase: FocusPhase = FocusPhase.Summary,
    ) {
        finalizeBackgroundAway(resetOnLongGap = false)
        countScreenUpPauseInterruptionIfNeeded()
        val awaySeconds = (backgroundAwayMillis / 1_000L).toInt()
        val pauseSeconds = (currentPausedMillis() / 1_000L).toInt()
        val interruptions = currentRoundInterruptionCount()
        val flowOvertimeSeconds = currentFlowOvertimeSeconds()
        val report = FocusRoundReport(
            id = SystemClock.elapsedRealtime(),
            completed = completed,
            focusedSeconds = focusedSeconds,
            flowOvertimeSeconds = flowOvertimeSeconds,
            quietSeconds = quietSeconds,
            screenInteractionCount = screenInteractionCount,
            phoneDownSeconds = phoneDownSeconds,
            faceDownAssistEnabled = faceDownAssistEnabled,
            softGlanceCount = softGlanceCount,
            interruptionCount = interruptions,
            awaySeconds = awaySeconds,
            pauseSeconds = pauseSeconds,
        )
        recordFocusSession(
            outcome = FocusSessionOutcome.FocusRoundEnded,
            focusSecondsValue = focusedSeconds,
            plannedFocusSecondsValue = plannedFocusSeconds,
            flowOvertimeSecondsValue = flowOvertimeSeconds,
            breakSecondsValue = 0,
            awaySecondsValue = awaySeconds,
            interruptionCountValue = interruptions,
        )
        val adaptation = adaptFocusRound(
            report = report,
            currentFocusMinutes = focusMinutes,
            currentBreakMinutes = breakMinutes,
            sessionFocusedSeconds = dayFocusedSeconds + focusedSeconds,
            cleanStreak = cleanStreak,
            interruptedStreak = interruptedStreak,
            completedLeavesThisSession = completedLeavesThisSession,
        )
        focusMinutes = adaptation.nextFocusMinutes
        suggestedFocusMinutes = adaptation.nextFocusMinutes
        deepFocusMinutes = adaptation.deepFocusMinutes
        breakMinutes = adaptation.nextBreakMinutes
        dayFocusedSeconds += focusedSeconds
        dayCompletedRounds += if (completed) 1 else 0
        dayInterruptionCount += interruptions
        dayAwaySeconds += awaySeconds
        dayPausedSeconds += pauseSeconds
        dayQuietSeconds += quietSeconds
        dayScreenInteractionCount += screenInteractionCount
        dayPhoneDownSeconds += phoneDownSeconds
        if (adaptation.isCleanRound) {
            cleanStreak += 1
            interruptedStreak = 0
        } else {
            interruptedStreak += 1
            cleanStreak = 0
        }
        val updatedReport = report.copy(suggestion = adaptation.message)
        lastReport = updatedReport
        notificationEvent = FocusNotificationEvent(
            id = report.id,
            title = "Round done",
            text = adaptation.message,
        )
        if (nextPhase == FocusPhase.BreakChoice) {
            remainingSeconds = breakMinutes * SecondsPerMinute
        }
        setPhase(nextPhase)
        publishDayState(lastRound = updatedReport)
    }

    fun persistPartialFocusProgress(): FocusRoundReport? {
        if (phase !in FocusTrackingPhases && phase != FocusPhase.Paused) return null
        finalizeBackgroundAway(resetOnLongGap = false)
        countScreenUpPauseInterruptionIfNeeded()

        val awaySeconds = (backgroundAwayMillis / 1_000L).toInt()
        val pauseSeconds = (currentPausedMillis() / 1_000L).toInt()
        val interruptions = currentRoundInterruptionCount()
        val flowOvertimeSeconds = currentFlowOvertimeSeconds()
        val report = FocusRoundReport(
            id = SystemClock.elapsedRealtime(),
            completed = false,
            focusedSeconds = focusedSeconds,
            flowOvertimeSeconds = flowOvertimeSeconds,
            quietSeconds = quietSeconds,
            screenInteractionCount = screenInteractionCount,
            phoneDownSeconds = phoneDownSeconds,
            faceDownAssistEnabled = faceDownAssistEnabled,
            softGlanceCount = softGlanceCount,
            interruptionCount = interruptions,
            awaySeconds = awaySeconds,
            pauseSeconds = pauseSeconds,
        )
        if (
            focusedSeconds == 0 &&
            quietSeconds == 0 &&
            phoneDownSeconds == 0 &&
            awaySeconds == 0 &&
            pauseSeconds == 0 &&
            interruptions == 0
        ) {
            return null
        }

        recordFocusSession(
            outcome = FocusSessionOutcome.FocusStopped,
            focusSecondsValue = focusedSeconds,
            plannedFocusSecondsValue = plannedFocusSeconds,
            flowOvertimeSecondsValue = flowOvertimeSeconds,
            breakSecondsValue = 0,
            awaySecondsValue = awaySeconds,
            interruptionCountValue = interruptions,
        )
        dayFocusedSeconds += focusedSeconds
        dayInterruptionCount += interruptions
        dayAwaySeconds += awaySeconds
        dayPausedSeconds += pauseSeconds
        dayQuietSeconds += quietSeconds
        dayScreenInteractionCount += screenInteractionCount
        dayPhoneDownSeconds += phoneDownSeconds
        resetRoundMetrics()
        return report
    }

    fun persistPartialBreakProgress() {
        if (phase !in BreakTrackingPhases) return
        val elapsedBreakSeconds = currentBreakElapsedSeconds()
        val unrecordedBreakSeconds = (elapsedBreakSeconds - recordedBreakSeconds).coerceAtLeast(0)
        if (unrecordedBreakSeconds == 0) return
        recordFocusSession(
            outcome = FocusSessionOutcome.BreakEnded,
            focusSecondsValue = 0,
            plannedFocusSecondsValue = 0,
            breakSecondsValue = unrecordedBreakSeconds,
        )
        dayBreakSeconds += unrecordedBreakSeconds
        recordedBreakSeconds = elapsedBreakSeconds
    }

    fun finishBreak() {
        val recoveryStartedBeforeFinish = recoveryStartedAtMillis
        persistPartialBreakProgress()
        notificationEvent = FocusNotificationEvent(
            id = SystemClock.elapsedRealtime(),
            title = "Break done",
            text = "Ready when you are.",
        )
        remainingSeconds = focusMinutes * SecondsPerMinute
        recordedBreakSeconds = 0
        setPhase(if (leafDoneChoicePending) FocusPhase.LeafDone else FocusPhase.Ready)
        if (recoveryStartedBeforeFinish != 0L) {
            recoveryStartedAtMillis = recoveryStartedBeforeFinish
        }
        publishDayState()
    }

    fun chooseBreakLength() {
        if (phase == FocusPhase.FlowOvertime) {
            completeFocusRound(
                completed = true,
                nextPhase = FocusPhase.BreakChoice,
            )
            return
        }
        val partialReport = persistPartialFocusProgress()
        if (partialReport != null) {
            val interruptionLoad = partialReport.interruptionLoad()
            val cleanBreakChoice = interruptionLoad == 0 &&
                partialReport.focusedSeconds >= MinFocusMinutes * SecondsPerMinute
            breakMinutes = adaptiveBreakMinutes(
                actualFocusSeconds = partialReport.focusedSeconds,
                currentBreakMinutes = breakMinutes,
                interruptionLoad = interruptionLoad,
                sessionFocusedSeconds = dayFocusedSeconds,
                cleanStreak = cleanStreak,
                interruptedStreak = interruptedStreak,
                completedLeavesThisSession = completedLeavesThisSession,
                isCleanRound = cleanBreakChoice,
            )
            if (interruptionLoad > 0) {
                interruptedStreak += 1
                cleanStreak = 0
            }
            lastReport = partialReport.copy(
                suggestion = if (interruptionLoad > 0) {
                    "Take the break. The next start can be smaller and cleaner."
                } else {
                    "Break chosen. Keep the restart light."
                },
            )
        }
        remainingSeconds = breakMinutes * SecondsPerMinute
        setPhase(FocusPhase.BreakChoice)
        publishDayState(lastRound = lastReport)
    }

    fun extendFlowRound(minutes: Int = suggestedFlowExtensionMinutes()) {
        if (phase != FocusPhase.FlowOvertime) return
        bankedFlowOvertimeSeconds = currentFlowOvertimeSeconds()
        val extensionSeconds = minutes.coerceIn(MinFocusMinutes, MaxFocusMinutes) * SecondsPerMinute
        plannedFocusSeconds += extensionSeconds
        remainingSeconds = extensionSeconds
        setPhase(FocusPhase.Focus)
        roundSerial += 1
        publishDayState()
    }

    fun requestDismiss() {
        persistPartialFocusProgress()
        persistPartialBreakProgress()
        val hasSessionRhythm = dayFocusedSeconds > 0 ||
            dayBreakSeconds > 0 ||
            focusedSeconds > 0 ||
            lastReport != null
        recoveryStartedAtMillis = when {
            recoveryStartedAtMillis != 0L -> recoveryStartedAtMillis
            hasSessionRhythm -> SystemClock.elapsedRealtime()
            else -> 0L
        }
        publishDayState(recoveryStartedAt = recoveryStartedAtMillis)
        onDismiss()
    }

    fun finishCurrentLeaf() {
        when (phase) {
            FocusPhase.Focus,
            FocusPhase.FlowOvertime -> completeFocusRound(
                completed = true,
                nextPhase = FocusPhase.LeafDone,
            )
            else -> {
                persistPartialFocusProgress()
                persistPartialBreakProgress()
            }
        }
        onMarkDone(today, currentTask.id)
        completedLeavesThisSession += 1
        leafDoneChoicePending = true
        setPhase(FocusPhase.LeafDone)
        publishDayState()
    }

    fun openNextLeaf() {
        val task = nextTask
        if (task == null) {
            requestDismiss()
            return
        }
        leafDoneChoicePending = false
        deepFocusMinutes = null
        setPhase(FocusPhase.Ready)
        remainingSeconds = focusMinutes * SecondsPerMinute
        onOpenTask(today, task.id, task.id in pullInCandidateIds)
        publishDayState()
    }

    BackHandler(onBack = ::requestDismiss)

    FocusLifecycleInterruptionTracker(
        phase = phase,
        onBackgrounded = {
            if (backgroundStartedAtMillis == 0L) {
                backgroundStartedAtMillis = SystemClock.elapsedRealtime()
            }
        },
        onForegrounded = {
            val wasFlowOvertime = phase == FocusPhase.FlowOvertime
            val awayStartedAt = backgroundStartedAtMillis
            val awayMillis = if (awayStartedAt == 0L) {
                0L
            } else {
                SystemClock.elapsedRealtime() - awayStartedAt
            }
            if (awayMillis >= LongMomentumResetMillis) {
                backgroundStartedAtMillis = 0L
                backgroundAwayMillis += awayMillis
                backgroundInterruptions += 1
                persistPartialFocusProgress()
                resetMomentumRhythm("Long interruption. Fresh start at 10 minutes; the day still counts.")
            } else if (finalizeBackgroundAway()) {
                resetMomentumRhythm("Long interruption. Fresh start at 10 minutes; the day still counts.")
            } else if (wasFlowOvertime && awayMillis >= InterruptionGraceMillis) {
                completeFocusRound(completed = true)
            }
        },
    )

    FocusNotificationEffect(
        phase = phase,
        remainingSeconds = remainingSeconds,
        taskTitle = currentTask.title,
        permissionGranted = notificationPermissionGranted,
        event = notificationEvent,
    )
    FocusCompletionCueEffect(event = completionCueEvent)

    val isAmbientFocus = phase in FocusTrackingPhases && screenSettled
    FocusKeepScreenOnEffect(enabled = phase in FocusTrackingPhases)
    FocusScreenDimmingEffect(enabled = isAmbientFocus)

    LaunchedEffect(focusModeNoticeSerial) {
        val serial = focusModeNoticeSerial
        if (focusModeNotice != null) {
            delay(FocusTopNoticeDurationMillis)
            if (focusModeNoticeSerial == serial) {
                focusModeNotice = null
            }
        }
    }

    LaunchedEffect(
        pendingFaceDownStartMinutes,
        latestPhoneDown,
        faceDownAssistEnabled,
        posture.isSupported,
        phase,
    ) {
        val pendingMinutes = pendingFaceDownStartMinutes ?: return@LaunchedEffect
        if (phase !in FocusStartGatePhases) {
            pendingFaceDownStartMinutes = null
            return@LaunchedEffect
        }
        if (!faceDownAssistEnabled || !posture.isSupported || latestPhoneDown) {
            if (phase == FocusPhase.Paused) {
                resumePausedFocus()
            } else {
                startFocusRound(pendingMinutes)
            }
        }
    }

    LaunchedEffect(
        phase,
        latestPhoneDown,
        faceDownAssistEnabled,
        posture.isSupported,
        pauseStartedAtMillis,
    ) {
        if (!faceDownAssistEnabled || !posture.isSupported) {
            if (phase == FocusPhase.Paused && pauseStartedAtMillis != 0L) {
                resumePausedFocus()
            }
            return@LaunchedEffect
        }

        if (phase in FocusTrackingPhases && !latestPhoneDown) {
            delay(FocusPhoneUpPauseGraceMillis)
            if (phase in FocusTrackingPhases && !latestPhoneDown && faceDownAssistEnabled && posture.isSupported) {
                pauseFocusForScreenUp()
            }
            return@LaunchedEffect
        }

        if (phase == FocusPhase.Paused && latestPhoneDown && pauseStartedAtMillis != 0L) {
            resumePausedFocus()
        }
    }

    LaunchedEffect(
        phase,
        pauseStartedAtMillis,
        screenUpPauseInterruptionCounted,
    ) {
        if (
            phase != FocusPhase.Paused ||
            pauseStartedAtMillis == 0L ||
            screenUpPauseInterruptionCounted
        ) {
            return@LaunchedEffect
        }
        val remainingGraceMillis = (InterruptionGraceMillis -
            (SystemClock.elapsedRealtime() - pauseStartedAtMillis)).coerceAtLeast(0L)
        delay(remainingGraceMillis)
        countScreenUpPauseInterruptionIfNeeded()
        publishDayState()
    }

    LaunchedEffect(startAutomatically, currentTask.id) {
        if (startAutomatically && !autoStarted) {
            autoStarted = true
            requestFocusStart(focusMinutes)
        }
    }

    LaunchedEffect(phase, roundSerial) {
        if (phase !in TimerRunningPhases) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            if (phase != FocusPhase.FlowOvertime || backgroundStartedAtMillis == 0L) {
                remainingSeconds -= 1
            }
            if (phase in FocusTrackingPhases) {
                val isForegroundFocus = backgroundStartedAtMillis == 0L
                if (isForegroundFocus) {
                    focusedSeconds += 1
                    val lastInteractionAt = lastInteractionAtMillis
                    if (
                        lastInteractionAt != 0L &&
                        SystemClock.elapsedRealtime() - lastInteractionAt >= QuietScreenThresholdMillis
                    ) {
                        screenSettled = true
                        quietSeconds += 1
                    } else {
                        screenSettled = false
                    }
                    if (latestPhoneDown) phoneDownSeconds += 1
                }
            }
            if (phase == FocusPhase.Focus && remainingSeconds <= 0) {
                completionCueEvent = FocusCompletionCueEvent(
                    id = SystemClock.elapsedRealtime(),
                    kind = FocusCompletionCueKind.FocusEnded,
                )
                setPhase(FocusPhase.FlowOvertime)
                return@LaunchedEffect
            }
            if (phase == FocusPhase.Break && remainingSeconds <= 0) {
                persistPartialBreakProgress()
                remainingSeconds = 0
                completionCueEvent = FocusCompletionCueEvent(
                    id = SystemClock.elapsedRealtime(),
                    kind = FocusCompletionCueKind.BreakEnded,
                )
                notificationEvent = FocusNotificationEvent(
                    id = SystemClock.elapsedRealtime(),
                    title = "Break done",
                    text = "Ready when you are.",
                )
                setPhase(FocusPhase.BreakEnded)
                return@LaunchedEffect
            }
        }
    }

    AdaptiveFocusModeContent(
        task = currentTask,
        sourceDocuments = project.plan.sourceDocuments,
        hasNextTask = nextTask != null,
        breakImpact = breakImpact,
        phase = phase,
        focusMinutes = focusMinutes,
        deepFocusMinutes = deepFocusMinutes,
        flowExtensionMinutes = suggestedFlowExtensionMinutes(),
        focusMinutesManuallyEdited = focusMinutesManuallyEdited,
        breakMinutes = breakMinutes,
        remainingSeconds = remainingSeconds,
        focusedSeconds = focusedSeconds,
        sessionFocusedSeconds = dayFocusedSeconds + if (phase in FocusTrackingPhases) {
            focusedSeconds
        } else {
            0
        },
        sessionBreakSeconds = dayBreakSeconds + if (phase == FocusPhase.Break) {
            currentUnrecordedBreakSeconds()
        } else if (phase == FocusPhase.BreakEnded) {
            currentUnrecordedBreakSeconds()
        } else {
            0
        },
        completedLeavesThisSession = completedLeavesThisSession,
        quietSeconds = quietSeconds,
        screenInteractionCount = screenInteractionCount,
        posture = posture,
        faceDownAssistEnabled = faceDownAssistEnabled,
        focusModeNotice = focusModeNotice,
        lastReport = lastReport,
        notificationPermissionGranted = notificationPermissionGranted,
        currentInterruptionCount = dayInterruptionCount + if (phase in FocusTrackingPhases || phase == FocusPhase.Paused) {
            currentRoundInterruptionCount()
        } else {
            0
        },
        faceDownStartPending = pendingFaceDownStartMinutes != null &&
            phase in FocusStartGatePhases &&
            faceDownAssistEnabled &&
            posture.isSupported,
        onRequestNotifications = { notificationPermissionGranted = it },
        onFaceDownAssistChange = { enabled ->
            updateFaceDownAssist(enabled)
        },
        onFocusMinutesChange = { minutes ->
            if (phase == FocusPhase.Ready) {
                val updatedMinutes = minutes.coerceIn(MinFocusMinutes, MaxFocusMinutes)
                deepFocusMinutes = null
                focusMinutes = updatedMinutes
                remainingSeconds = updatedMinutes * SecondsPerMinute
                publishDayState()
            }
        },
        onFocusInteraction = {
            if (phase in FocusTrackingPhases) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastInteractionAtMillis >= InteractionCountThrottleMillis) {
                    screenInteractionCount += 1
                }
                lastInteractionAtMillis = now
                screenSettled = false
            }
        },
        onDismiss = ::requestDismiss,
        onStartRound = { requestFocusStart(focusMinutes) },
        onStartFocusMinutes = { minutes -> requestFocusStart(minutes) },
        onEndFocus = ::requestDismiss,
        onTakeBreak = ::chooseBreakLength,
        onExtendFlow = ::extendFlowRound,
        onStartBreak = ::startBreak,
        onSkipBreak = { finishBreak() },
        onContinueLeaf = { requestFocusStart(focusMinutes) },
        onMarkDone = ::finishCurrentLeaf,
        onOpenNextLeaf = ::openNextLeaf,
        ambientDimmed = isAmbientFocus,
        modifier = modifier,
    )
}

@Composable
private fun AdaptiveFocusModeContent(
    task: GeneratedStudyBlock,
    sourceDocuments: List<StudySourceDocument>,
    hasNextTask: Boolean,
    breakImpact: FocusBreakImpact,
    phase: FocusPhase,
    focusMinutes: Int,
    deepFocusMinutes: Int?,
    flowExtensionMinutes: Int,
    focusMinutesManuallyEdited: Boolean,
    breakMinutes: Int,
    remainingSeconds: Int,
    focusedSeconds: Int,
    sessionFocusedSeconds: Int,
    sessionBreakSeconds: Int,
    completedLeavesThisSession: Int,
    quietSeconds: Int,
    screenInteractionCount: Int,
    posture: FocusPostureReading,
    faceDownAssistEnabled: Boolean,
    focusModeNotice: String?,
    faceDownStartPending: Boolean,
    lastReport: FocusRoundReport?,
    currentInterruptionCount: Int,
    notificationPermissionGranted: Boolean,
    onRequestNotifications: (Boolean) -> Unit,
    onFaceDownAssistChange: (Boolean) -> Unit,
    onFocusMinutesChange: (Int) -> Unit,
    onFocusInteraction: () -> Unit,
    onDismiss: () -> Unit,
    onStartRound: () -> Unit,
    onStartFocusMinutes: (Int) -> Unit,
    onEndFocus: () -> Unit,
    onTakeBreak: () -> Unit,
    onExtendFlow: () -> Unit,
    onStartBreak: (Int) -> Unit,
    onSkipBreak: () -> Unit,
    onContinueLeaf: () -> Unit,
    onMarkDone: () -> Unit,
    onOpenNextLeaf: () -> Unit,
    ambientDimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val chromeAlpha by animateFloatAsState(
            targetValue = if (ambientDimmed) 0f else 1f,
            animationSpec = tween(
                durationMillis = FocusQuietTransitionMillis,
                easing = FastOutSlowInEasing,
            ),
            label = "focus-quiet-chrome-alpha",
        )
        val contentModifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .trackFocusInteractions(
                enabled = phase in FocusTrackingPhases,
                onInteraction = onFocusInteraction,
            )
            .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 18.dp)

        BoxWithConstraints(
            modifier = contentModifier,
            contentAlignment = Alignment.Center,
        ) {
            val layoutSpec = focusLayoutSpec(maxWidth = maxWidth, maxHeight = maxHeight)

            FocusTimerStage(
                phase = phase,
                remainingSeconds = remainingSeconds,
                focusMinutes = focusMinutes,
                focusMinutesManuallyEdited = focusMinutesManuallyEdited,
                breakMinutes = breakMinutes,
                lastReport = lastReport,
                onFocusMinutesChange = onFocusMinutesChange,
                quietMode = ambientDimmed,
                timerSize = layoutSpec.timerSize,
                suggestionOffsetY = layoutSpec.suggestionOffsetY,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!ambientDimmed || chromeAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(chromeAlpha),
                ) {
                    FocusTaskTopBar(
                        posture = posture,
                        faceDownAssistEnabled = faceDownAssistEnabled,
                        onFaceDownAssistChange = onFaceDownAssistChange,
                        onEndFocus = onEndFocus,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )

                    FocusTaskHeading(
                        task = task,
                        sourceDocuments = sourceDocuments,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = layoutSpec.headingOffsetY),
                    )
                    FocusBottomPanel(
                        phase = phase,
                        hasNextTask = hasNextTask,
                        breakImpact = breakImpact,
                        focusMinutes = focusMinutes,
                        deepFocusMinutes = deepFocusMinutes,
                        flowExtensionMinutes = flowExtensionMinutes,
                        breakMinutes = breakMinutes,
                        lastReport = lastReport,
                        sessionFocusedSeconds = sessionFocusedSeconds,
                        completedLeavesThisSession = completedLeavesThisSession,
                        notificationPermissionGranted = notificationPermissionGranted,
                        faceDownStartPending = faceDownStartPending,
                        onRequestNotifications = onRequestNotifications,
                        onStartRound = onStartRound,
                        onStartFocusMinutes = onStartFocusMinutes,
                        onTakeBreak = onTakeBreak,
                        onExtendFlow = onExtendFlow,
                        onStartBreak = onStartBreak,
                        onSkipBreak = onSkipBreak,
                        onContinueLeaf = onContinueLeaf,
                        onMarkDone = onMarkDone,
                        onOpenNextLeaf = onOpenNextLeaf,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = layoutSpec.panelOffsetY),
                    )
                    FocusSessionLine(
                        sessionFocusedSeconds = sessionFocusedSeconds,
                        sessionBreakSeconds = sessionBreakSeconds,
                        currentInterruptionCount = currentInterruptionCount,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = layoutSpec.sessionLineBottomPadding),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = focusModeNotice != null && !ambientDimmed,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = FocusTopNoticeAnimationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = FocusTopNoticeAnimationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) { -it / 2 },
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = FocusTopNoticeAnimationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = FocusTopNoticeAnimationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 20.dp, top = 74.dp, end = 20.dp),
        ) {
            FocusTopNotice(message = focusModeNotice.orEmpty())
        }
    }
}

private data class FocusLayoutSpec(
    val timerSize: Dp,
    val headingOffsetY: Dp,
    val panelOffsetY: Dp,
    val suggestionOffsetY: Dp,
    val sessionLineBottomPadding: Dp,
)

private fun focusLayoutSpec(maxWidth: Dp, maxHeight: Dp): FocusLayoutSpec {
    val widthScale = (maxWidth.value / FocusBaselineContentWidthDp).coerceIn(FocusMinLayoutScale, 1f)
    val heightScale = (maxHeight.value / FocusBaselineContentHeightDp).coerceIn(FocusMinLayoutScale, 1f)
    val scale = minOf(widthScale, heightScale)

    val timerSizeValue = (FocusBaselineTimerSizeDp * scale)
        .coerceAtLeast(FocusMinimumTimerSizeDp)
    val headingOffsetValue = guardedHeadingOffset(
        maxHeight = maxHeight,
        idealOffset = FocusBaselineHeadingOffsetDp * scale,
    )
    val panelOffsetValue = guardedPanelOffset(
        maxHeight = maxHeight,
        idealOffset = FocusBaselinePanelOffsetDp * scale,
    )
    val suggestionOffsetValue = (timerSizeValue / 2f) + (FocusSuggestionGapDp * scale)

    return FocusLayoutSpec(
        timerSize = timerSizeValue.dp,
        headingOffsetY = headingOffsetValue.dp,
        panelOffsetY = panelOffsetValue.dp,
        suggestionOffsetY = suggestionOffsetValue.dp,
        sessionLineBottomPadding = (FocusBaselineSessionLineBottomPaddingDp * scale)
            .coerceAtLeast(FocusMinimumSessionLineBottomPaddingDp)
            .dp,
    )
}

private fun guardedHeadingOffset(maxHeight: Dp, idealOffset: Float): Float {
    val minimumHeadingCenterY = if (maxHeight.value < FocusVeryTightContentHeightDp) {
        FocusVeryTightHeadingCenterYDp
    } else {
        FocusTightHeadingCenterYDp
    }
    val highestAllowedOffset = minimumHeadingCenterY - (maxHeight.value / 2f)
    return maxOf(idealOffset, highestAllowedOffset)
}

private fun guardedPanelOffset(maxHeight: Dp, idealOffset: Float): Float {
    val bottomReserve = if (maxHeight.value < FocusTightContentHeightDp) {
        FocusTightBottomReserveDp
    } else {
        FocusBaselineBottomReserveDp
    }
    val lowestAllowedOffset = (maxHeight.value / 2f) - bottomReserve
    return minOf(idealOffset, lowestAllowedOffset)
        .coerceAtLeast(FocusMinimumPanelOffsetDp)
}

@Composable
private fun FocusTaskTopBar(
    posture: FocusPostureReading,
    faceDownAssistEnabled: Boolean,
    onFaceDownAssistChange: (Boolean) -> Unit,
    onEndFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
        ) {
            FocusEndTopButton(
                onClick = onEndFocus,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Eco,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.focus_current_leaf),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FocusFaceDownTopToggle(
                posture = posture,
                enabled = faceDownAssistEnabled,
                onEnabledChange = onFaceDownAssistChange,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun FocusTaskHeading(
    task: GeneratedStudyBlock,
    sourceDocuments: List<StudySourceDocument>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = focusTaskMetaWithPages(
                task = task,
                sourceDocuments = sourceDocuments,
            ).ifBlank { task.title },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FocusEndTopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "End",
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.78f),
        maxLines = 1,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun focusTaskMetaWithPages(
    task: GeneratedStudyBlock,
    sourceDocuments: List<StudySourceDocument>,
): String = listOfNotNull(
    taskPdfLabel(task, sourceDocuments),
    taskPageLabel(task),
).joinToString(" · ")

@Composable
private fun taskPdfLabel(
    task: GeneratedStudyBlock,
    sourceDocuments: List<StudySourceDocument>,
): String? {
    val documentId = task.sourceRefs.firstOrNull()?.documentId ?: return null
    val document = sourceDocuments.firstOrNull { it.id == documentId || it.uploadDocumentId == documentId }
        ?: return null
    return stringResource(R.string.pdf_order_label, document.order)
}

@Composable
private fun focusTaskMeta(
    phase: FocusPhase,
    task: GeneratedStudyBlock,
    focusMinutes: Int,
    focusMinutesManuallyEdited: Boolean,
): String = when (phase) {
    FocusPhase.Ready -> {
        val roundLabel = if (focusMinutesManuallyEdited) {
            "${formatMinutes(focusMinutes)} round"
        } else {
            "${formatMinutes(focusMinutes)} suggested"
        }
        "${taskTypeLabel(task.taskType)} · ${formatMinutes(task.likelyStudyMinutes)} leaf · $roundLabel"
    }
    FocusPhase.Focus,
    FocusPhase.FlowOvertime,
    FocusPhase.Paused,
        -> "${taskTypeLabel(task.taskType)} · focus in progress"
    FocusPhase.BreakChoice -> "Choose a break length"
    FocusPhase.Break -> "Break - no interruption tracking"
    FocusPhase.BreakEnded -> "Break ended - restart when ready"
    FocusPhase.Summary -> "Round complete - choose the next move"
    FocusPhase.LeafDone -> "Leaf complete - choose the next move"
}

@Composable
private fun FocusFaceDownTopToggle(
    posture: FocusPostureReading,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOn = enabled && posture.isSupported
    val tint = if (isOn) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    }
    Box(
        modifier = modifier
            .height(42.dp)
            .border(
                width = 1.dp,
                color = if (isOn) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
                },
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(enabled = posture.isSupported) {
                onEnabledChange(!enabled)
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MobileOff,
            contentDescription = "Phone-down assist",
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun FocusTopNotice(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MobileOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FocusTimerStage(
    phase: FocusPhase,
    remainingSeconds: Int,
    focusMinutes: Int,
    focusMinutesManuallyEdited: Boolean,
    breakMinutes: Int,
    lastReport: FocusRoundReport?,
    onFocusMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    quietMode: Boolean = false,
    timerSize: Dp = FocusBaselineTimerSizeDp.dp,
    suggestionOffsetY: Dp = FocusBaselineSuggestionOffsetDp.dp,
) {
    val totalSeconds = when (phase) {
        FocusPhase.BreakChoice,
        FocusPhase.Break,
        FocusPhase.BreakEnded,
            -> breakMinutes * SecondsPerMinute
        FocusPhase.Focus,
        FocusPhase.FlowOvertime,
        FocusPhase.Paused,
        FocusPhase.Ready,
        FocusPhase.Summary,
        FocusPhase.LeafDone,
            -> focusMinutes * SecondsPerMinute
    }.coerceAtLeast(1)
    val progress = when (phase) {
        FocusPhase.Ready,
        FocusPhase.BreakChoice,
            -> 0f
        FocusPhase.Summary,
        FocusPhase.LeafDone,
        FocusPhase.FlowOvertime,
        FocusPhase.BreakEnded,
            -> 1f
        else -> 1f - (remainingSeconds.toFloat() / totalSeconds)
    }.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = FocusTimerRingAnimationMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "focus-timer-ring-progress",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        FocusTimerRing(
            phase = phase,
            progress = animatedProgress,
            timeText = timerLabel(phase, remainingSeconds, focusMinutes),
            focusMinutes = focusMinutes,
            focusMinutesManuallyEdited = focusMinutesManuallyEdited,
            lastReport = lastReport,
            onFocusMinutesChange = onFocusMinutesChange,
            quietMode = quietMode,
            modifier = Modifier.size(timerSize),
        )
        if (phase == FocusPhase.Ready && !quietMode) {
            Text(
                text = focusSuggestionReason(
                    lastReport = lastReport,
                    focusMinutesManuallyEdited = focusMinutesManuallyEdited,
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = suggestionOffsetY)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FocusTimerAdjustButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
        alpha = if (enabled) 0.74f else 0.2f,
    )
    Box(
        modifier = modifier
            .size(30.dp)
            .border(
                width = 1.dp,
                color = tint.copy(alpha = if (enabled) 0.34f else 0.16f),
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun FocusTimerReadyContent(
    timeText: String,
    focusMinutes: Int,
    focusMinutesManuallyEdited: Boolean,
    onFocusMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FocusTimerAdjustButton(
                icon = Icons.Default.Remove,
                enabled = focusMinutes > MinFocusMinutes,
                onClick = { onFocusMinutesChange(manualFocusStep(focusMinutes, -1)) },
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            FocusTimerAdjustButton(
                icon = Icons.Default.Add,
                enabled = focusMinutes < MaxFocusMinutes,
                onClick = { onFocusMinutesChange(manualFocusStep(focusMinutes, 1)) },
            )
        }
        if (!focusMinutesManuallyEdited) {
            Text(
                text = "Suggested",
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 54.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FocusTimerStatusContent(
    phase: FocusPhase,
    timeText: String,
    lastReport: FocusRoundReport?,
    quietMode: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (quietMode) return@Column
        Text(
            text = focusTimerStatusTitle(phase),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = when (phase) {
                FocusPhase.Break,
                FocusPhase.BreakEnded,
                    -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
                FocusPhase.Summary,
                FocusPhase.LeafDone,
                    -> MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                FocusPhase.FlowOvertime -> MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private fun focusSuggestionReason(
    lastReport: FocusRoundReport?,
    focusMinutesManuallyEdited: Boolean,
): String {
    if (focusMinutesManuallyEdited) {
        return "Custom round. Start with what feels doable."
    }
    val report = lastReport ?: return "Short enough to start, long enough to count."
    return when {
        report.awaySeconds >= RhythmBreakSeconds -> "Keep restart easy after the gap."
        report.interruptionLoad() > 0 -> "A little turbulence. Smaller restart, less drama."
        report.completed -> "You stayed with it. Momentum gets a gentle nudge."
        else -> "Protect the restart. Momentum first."
    }
}

private fun focusTimerStatusTitle(phase: FocusPhase): String = when (phase) {
    FocusPhase.Ready -> "Suggested start"
    FocusPhase.Focus -> "Focus"
    FocusPhase.FlowOvertime -> "Flow"
    FocusPhase.Paused -> "Paused"
    FocusPhase.BreakChoice -> "Suggested break"
    FocusPhase.Break -> "Break"
    FocusPhase.BreakEnded -> "Break ended"
    FocusPhase.Summary -> "Round complete"
    FocusPhase.LeafDone -> "Leaf complete"
}

@Composable
private fun FocusTimerRing(
    phase: FocusPhase,
    progress: Float,
    timeText: String,
    focusMinutes: Int,
    focusMinutesManuallyEdited: Boolean,
    lastReport: FocusRoundReport?,
    onFocusMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    quietMode: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val isActive = phase in TimerRunningPhases
    val isFlowOvertime = phase == FocusPhase.FlowOvertime
    val pulse = if (isActive) {
        0.985f + 0.015f * kotlin.math.sin((progress * kotlin.math.PI).toFloat())
    } else {
        0.985f
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.37f * pulse
            val ringStroke = size.minDimension * 0.034f
            val softStroke = size.minDimension * 0.01f
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)

            if (isFlowOvertime) {
                drawCircle(
                    color = primary.copy(alpha = 0.12f),
                    radius = radius * 1.24f,
                    center = center,
                    style = Stroke(width = softStroke * 2.2f, cap = StrokeCap.Round),
                )
                drawCircle(
                    color = primary.copy(alpha = 0.22f),
                    radius = radius * 1.1f,
                    center = center,
                    style = Stroke(width = softStroke * 1.25f, cap = StrokeCap.Round),
                )
            }
            drawCircle(
                color = primary.copy(alpha = if (isFlowOvertime) 0.16f else 0.1f),
                radius = radius * 1.18f,
                center = center,
                style = Stroke(width = softStroke, cap = StrokeCap.Round),
            )
            drawCircle(
                color = track.copy(alpha = if (isFlowOvertime) 0.16f else 0.24f),
                radius = radius,
                center = center,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = primary.copy(alpha = if (isFlowOvertime) 0.98f else 0.88f),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round),
            )
        }
        if (phase == FocusPhase.Ready) {
            FocusTimerReadyContent(
                timeText = timeText,
                focusMinutes = focusMinutes,
                focusMinutesManuallyEdited = focusMinutesManuallyEdited,
                onFocusMinutesChange = onFocusMinutesChange,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            FocusTimerStatusContent(
                phase = phase,
                timeText = timeText,
                lastReport = lastReport,
                quietMode = quietMode,
            )
        }
    }
}

@Composable
private fun FocusBottomPanel(
    phase: FocusPhase,
    hasNextTask: Boolean,
    breakImpact: FocusBreakImpact,
    focusMinutes: Int,
    deepFocusMinutes: Int?,
    flowExtensionMinutes: Int,
    breakMinutes: Int,
    lastReport: FocusRoundReport?,
    sessionFocusedSeconds: Int,
    completedLeavesThisSession: Int,
    notificationPermissionGranted: Boolean,
    faceDownStartPending: Boolean,
    onRequestNotifications: (Boolean) -> Unit,
    onStartRound: () -> Unit,
    onStartFocusMinutes: (Int) -> Unit,
    onTakeBreak: () -> Unit,
    onExtendFlow: () -> Unit,
    onStartBreak: (Int) -> Unit,
    onSkipBreak: () -> Unit,
    onContinueLeaf: () -> Unit,
    onMarkDone: () -> Unit,
    onOpenNextLeaf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelGap = if (phase == FocusPhase.LeafDone) 14.dp else 10.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(panelGap),
    ) {
        if (!notificationPermissionGranted && phase == FocusPhase.Ready) {
            FocusNotificationMiniButton(onPermissionResult = onRequestNotifications)
        }
        FocusControls(
            phase = phase,
            hasNextTask = hasNextTask,
            breakImpact = breakImpact,
            focusMinutes = focusMinutes,
            deepFocusMinutes = deepFocusMinutes,
            flowExtensionMinutes = flowExtensionMinutes,
            breakMinutes = breakMinutes,
            lastReport = lastReport,
            sessionFocusedSeconds = sessionFocusedSeconds,
            completedLeavesThisSession = completedLeavesThisSession,
            faceDownStartPending = faceDownStartPending,
            onStartRound = onStartRound,
            onStartFocusMinutes = onStartFocusMinutes,
            onTakeBreak = onTakeBreak,
            onExtendFlow = onExtendFlow,
            onStartBreak = onStartBreak,
            onSkipBreak = onSkipBreak,
            onContinueLeaf = onContinueLeaf,
            onMarkDone = onMarkDone,
            onOpenNextLeaf = onOpenNextLeaf,
        )
    }
}

@Composable
private fun FocusSessionLine(
    sessionFocusedSeconds: Int,
    sessionBreakSeconds: Int,
    currentInterruptionCount: Int,
    modifier: Modifier = Modifier,
) {
    val sessionMinutes = (sessionFocusedSeconds / SecondsPerMinute).coerceAtLeast(0)
    val breakMinutes = (sessionBreakSeconds / SecondsPerMinute).coerceAtLeast(0)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Focus ${formatMinutes(sessionMinutes)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
        )
        Text(
            text = "  ·  ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
        )
        Text(
            text = "Interruptions $currentInterruptionCount",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
        )
        Text(
            text = "  ·  ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
        )
        Text(
            text = "Break ${formatMinutes(breakMinutes)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
        )
    }
}

@Composable
private fun FocusNotificationMiniButton(onPermissionResult: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onPermissionResult(granted)
    }
    TextButton(
        onClick = {
            if (hasFocusNotificationPermission(context)) {
                onPermissionResult(true)
            } else {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        modifier = Modifier.height(36.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Allow focus nudges",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
        )
    }
}

@Composable
private fun FocusControls(
    phase: FocusPhase,
    hasNextTask: Boolean,
    breakImpact: FocusBreakImpact,
    focusMinutes: Int,
    deepFocusMinutes: Int?,
    flowExtensionMinutes: Int,
    breakMinutes: Int,
    lastReport: FocusRoundReport?,
    sessionFocusedSeconds: Int,
    completedLeavesThisSession: Int,
    faceDownStartPending: Boolean,
    onStartRound: () -> Unit,
    onStartFocusMinutes: (Int) -> Unit,
    onTakeBreak: () -> Unit,
    onExtendFlow: () -> Unit,
    onStartBreak: (Int) -> Unit,
    onSkipBreak: () -> Unit,
    onContinueLeaf: () -> Unit,
    onMarkDone: () -> Unit,
    onOpenNextLeaf: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val waitingForScreenDown = faceDownStartPending && phase in FocusStartGatePhases
        if (waitingForScreenDown) {
            FocusStatusPill(
                label = if (phase == FocusPhase.Paused) {
                    "Screen down to continue"
                } else {
                    "Nice try. Screen down first"
                },
                icon = Icons.Default.MobileOff,
            )
            if (phase != FocusPhase.Paused) return@Column
        }
        when (phase) {
            FocusPhase.Ready -> {
                FocusActionPill(
                    label = stringResource(R.string.start_focus),
                    icon = Icons.Default.PlayArrow,
                    onClick = onStartRound,
                    primary = true,
                )
            }
            FocusPhase.Focus -> {
                FocusLiveActions(
                    onTakeBreak = onTakeBreak,
                    onMarkDone = onMarkDone,
                )
            }
            FocusPhase.FlowOvertime -> {
                FocusFlowActions(
                    extensionMinutes = flowExtensionMinutes,
                    onExtend = onExtendFlow,
                    onTakeBreak = onTakeBreak,
                    onMarkDone = onMarkDone,
                )
            }
            FocusPhase.Paused -> {
                if (waitingForScreenDown) {
                    FocusLiveActions(
                        onTakeBreak = onTakeBreak,
                        onMarkDone = onMarkDone,
                    )
                } else {
                    FocusActionPill(
                        label = "Continue focus",
                        icon = Icons.Default.PlayArrow,
                        onClick = onContinueLeaf,
                        primary = true,
                    )
                }
            }
            FocusPhase.Break,
            FocusPhase.BreakEnded,
                -> {
                FocusActionPill(
                    label = "Back to focus",
                    icon = Icons.Default.PlayArrow,
                    onClick = onSkipBreak,
                    primary = true,
                )
            }
            FocusPhase.BreakChoice -> {
                FocusBreakChoiceActions(
                    breakMinutes = breakMinutes,
                    impact = breakImpact,
                    onStartBreak = onStartBreak,
                )
            }
            FocusPhase.Summary -> {
                val leadWithMomentum = shouldLeadWithMomentum(
                    report = lastReport,
                    sessionFocusedSeconds = sessionFocusedSeconds,
                    completedLeavesThisSession = completedLeavesThisSession,
                )
                FocusRoundDecisionActions(
                    leadWithMomentum = leadWithMomentum,
                    focusMinutes = focusMinutes,
                    deepFocusMinutes = deepFocusMinutes,
                    breakMinutes = breakMinutes,
                    suggestion = lastReport?.suggestion.orEmpty(),
                    onTakeBreak = onTakeBreak,
                    onContinueLeaf = onContinueLeaf,
                    onStartFocusMinutes = onStartFocusMinutes,
                    onMarkDone = onMarkDone,
                )
            }
            FocusPhase.LeafDone -> {
                FocusLeafDoneActions(
                    hasNextTask = hasNextTask,
                    breakMinutes = breakMinutes,
                    onTakeBreak = onTakeBreak,
                    onOpenNextLeaf = onOpenNextLeaf,
                )
            }
        }
    }
}

@Composable
private fun FocusLiveActions(
    onTakeBreak: () -> Unit,
    onMarkDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FocusActionPill(
            label = "Take break",
            icon = Icons.Default.WbSunny,
            onClick = onTakeBreak,
            modifier = Modifier.weight(1f),
        )
        FocusActionPill(
            label = stringResource(R.string.focus_mark_done),
            icon = Icons.Default.Eco,
            onClick = onMarkDone,
            modifier = Modifier.weight(1f),
            primary = true,
        )
    }
}

@Composable
private fun FocusFlowActions(
    extensionMinutes: Int,
    onExtend: () -> Unit,
    onTakeBreak: () -> Unit,
    onMarkDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FocusActionPill(
                label = "Add ${formatMinutes(extensionMinutes)}",
                icon = Icons.Default.Add,
                onClick = onExtend,
                modifier = Modifier.weight(1f),
                primary = true,
            )
            FocusActionPill(
                label = stringResource(R.string.focus_mark_done),
                icon = Icons.Default.Eco,
                onClick = onMarkDone,
                modifier = Modifier.weight(1f),
            )
        }
        FocusActionPill(
            label = "Take break",
            icon = Icons.Default.WbSunny,
            onClick = onTakeBreak,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FocusBreakChoiceActions(
    breakMinutes: Int,
    impact: FocusBreakImpact,
    onStartBreak: (Int) -> Unit,
) {
    val suggested = breakMinutes.coerceIn(MinBreakMinutes, MaxBreakMinutes)
    val impactMessage = breakImpactMessage(suggested, impact)
    val alternatives = listOf(
        breakStep(suggested, -1),
        breakStep(suggested, 1),
    ).filter { it != suggested }.distinct()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Pick a break length",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            maxLines = 1,
        )
        Text(
            text = impactMessage.text,
            style = MaterialTheme.typography.bodySmall,
            color = if (impactMessage.isWarning) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.78f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FocusActionPill(
            label = "Suggested ${formatMinutes(suggested)}",
            icon = Icons.Default.WbSunny,
            onClick = { onStartBreak(suggested) },
            modifier = Modifier.fillMaxWidth(),
            primary = true,
        )
        if (alternatives.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                alternatives.forEach { minutes ->
                    FocusActionPill(
                        label = formatMinutes(minutes),
                        icon = Icons.Default.WbSunny,
                        onClick = { onStartBreak(minutes) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusRoundDecisionActions(
    leadWithMomentum: Boolean,
    focusMinutes: Int,
    deepFocusMinutes: Int?,
    breakMinutes: Int,
    suggestion: String,
    onTakeBreak: () -> Unit,
    onContinueLeaf: () -> Unit,
    onStartFocusMinutes: (Int) -> Unit,
    onMarkDone: () -> Unit,
) {
    val showDeepOption = leadWithMomentum && deepFocusMinutes != null
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Choose the next move",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            maxLines = 1,
        )
        if (suggestion.isNotBlank()) {
            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDeepOption) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FocusActionPill(
                    label = "Add ${formatMinutes(focusMinutes)}",
                    icon = Icons.Default.PlayArrow,
                    onClick = onContinueLeaf,
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
                FocusActionPill(
                    label = "Deep ${formatMinutes(deepFocusMinutes ?: focusMinutes)}",
                    icon = Icons.Default.Add,
                    onClick = { onStartFocusMinutes(deepFocusMinutes ?: focusMinutes) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FocusActionPill(
                    label = "Take break",
                    icon = Icons.Default.WbSunny,
                    onClick = onTakeBreak,
                    modifier = Modifier.weight(1f),
                )
                FocusActionPill(
                    label = stringResource(R.string.focus_mark_done),
                    icon = Icons.Default.Eco,
                    onClick = onMarkDone,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FocusActionPill(
                    label = if (leadWithMomentum) {
                        "Add ${formatMinutes(focusMinutes)}"
                    } else {
                        "Take break"
                    },
                    icon = if (leadWithMomentum) Icons.Default.PlayArrow else Icons.Default.WbSunny,
                    onClick = if (leadWithMomentum) onContinueLeaf else onTakeBreak,
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
                FocusActionPill(
                    label = if (leadWithMomentum) {
                        "Take break"
                    } else {
                        "Add ${formatMinutes(focusMinutes)}"
                    },
                    icon = if (leadWithMomentum) Icons.Default.WbSunny else Icons.Default.PlayArrow,
                    onClick = if (leadWithMomentum) onTakeBreak else onContinueLeaf,
                    modifier = Modifier.weight(1f),
                )
            }
            FocusActionPill(
                label = stringResource(R.string.focus_mark_done),
                icon = Icons.Default.Eco,
                onClick = onMarkDone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FocusLeafDoneActions(
    hasNextTask: Boolean,
    breakMinutes: Int,
    onTakeBreak: () -> Unit,
    onOpenNextLeaf: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Leaf finished. Keep the momentum civil.",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FocusActionPill(
                label = if (hasNextTask) "Next leaf" else "Back to today",
                icon = if (hasNextTask) Icons.Default.PlayArrow else null,
                onClick = onOpenNextLeaf,
                modifier = Modifier.weight(1f),
                primary = hasNextTask,
            )
            FocusActionPill(
                label = "Take break",
                icon = Icons.Default.WbSunny,
                onClick = onTakeBreak,
                modifier = Modifier.weight(1f),
                primary = !hasNextTask,
            )
        }
    }
}

@Composable
private fun FocusActionPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(18.dp)
    val height = 48.dp
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(height),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            FocusActionPillContent(label = label, icon = icon)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(height),
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            ),
        ) {
            FocusActionPillContent(label = label, icon = icon)
        }
    }
}

@Composable
private fun FocusStatusPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.64f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FocusActionPillContent(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(7.dp))
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun shouldLeadWithMomentum(
    report: FocusRoundReport?,
    sessionFocusedSeconds: Int,
    completedLeavesThisSession: Int,
): Boolean {
    if (report == null) return true
    if (!report.completed) return false

    val interruptionLoad = report.interruptionLoad()
    if (interruptionLoad > 0) return false

    val sessionMinutes = sessionFocusedSeconds / SecondsPerMinute
    if (sessionMinutes >= 45) return false
    if (completedLeavesThisSession >= 4) return false

    return true
}

@Composable
private fun FocusEmptyState(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.focus_no_task),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        TextButton(onClick = onDismiss) {
            Text("Close")
        }
    }
}

@Composable
private fun rememberNotificationPermissionState(): androidx.compose.runtime.MutableState<Boolean> {
    val context = LocalContext.current
    return remember(context) {
        mutableStateOf(hasFocusNotificationPermission(context))
    }
}

@Composable
private fun FocusLifecycleInterruptionTracker(
    phase: FocusPhase,
    onBackgrounded: () -> Unit,
    onForegrounded: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findActivity() as? LifecycleOwner }
    val latestPhase by rememberUpdatedState(phase)
    val latestBackgrounded by rememberUpdatedState(onBackgrounded)
    val latestForegrounded by rememberUpdatedState(onForegrounded)
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                    -> if (latestPhase in FocusTrackingPhases) latestBackgrounded()
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME,
                    -> latestForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun FocusKeepScreenOnEffect(enabled: Boolean) {
    if (!enabled) return
    val view = LocalView.current
    DisposableEffect(view) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
        }
    }
}

@Composable
private fun FocusScreenDimmingEffect(enabled: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, enabled) {
        if (activity == null || !enabled) return@DisposableEffect onDispose {}
        val window = activity.window
        val previousBrightness = window.attributes.screenBrightness
        val dimmedAttributes = window.attributes
        dimmedAttributes.screenBrightness = FocusScreenBrightness
        window.attributes = dimmedAttributes
        onDispose {
            val restoredAttributes = window.attributes
            restoredAttributes.screenBrightness = previousBrightness
            window.attributes = restoredAttributes
        }
    }
}

private fun Modifier.trackFocusInteractions(
    enabled: Boolean,
    onInteraction: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(enabled) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent()
                onInteraction()
            }
        }
    }
}

@Composable
private fun rememberFocusPostureReading(enabled: Boolean): FocusPostureReading {
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val accelerometer = remember(sensorManager) { sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val light = remember(sensorManager) { sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) }
    val isSupported = accelerometer != null
    var isFaceDown by remember { mutableStateOf(false) }
    var isStill by remember { mutableStateOf(false) }
    var isLowLight by remember { mutableStateOf(false) }
    DisposableEffect(sensorManager, accelerometer, light, enabled) {
        if (!enabled || sensorManager == null || accelerometer == null) {
            isFaceDown = false
            isStill = false
            isLowLight = false
            onDispose {}
        } else {
            var previousAcceleration: FloatArray? = null
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            val x = event.values.getOrNull(0) ?: 0f
                            val y = event.values.getOrNull(1) ?: 0f
                            val z = event.values.getOrNull(2) ?: 0f
                            isFaceDown = z < -7f
                            val previous = previousAcceleration
                            isStill = if (previous == null) {
                                false
                            } else {
                                val dx = x - previous[0]
                                val dy = y - previous[1]
                                val dz = z - previous[2]
                                sqrt(dx * dx + dy * dy + dz * dz) < 0.55f
                            }
                            previousAcceleration = floatArrayOf(x, y, z)
                        }
                        Sensor.TYPE_LIGHT -> {
                            val lux = event.values.firstOrNull() ?: Float.MAX_VALUE
                            isLowLight = lux <= 8f
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            if (light != null) {
                sensorManager.registerListener(listener, light, SensorManager.SENSOR_DELAY_NORMAL)
            } else {
                isLowLight = true
            }
            onDispose { sensorManager?.unregisterListener(listener) }
        }
    }
    return FocusPostureReading(
        isSupported = isSupported,
        isPhoneDown = enabled && isFaceDown && isStill && isLowLight,
    )
}

@Composable
private fun FocusNotificationEffect(
    phase: FocusPhase,
    remainingSeconds: Int,
    taskTitle: String,
    permissionGranted: Boolean,
    event: FocusNotificationEvent?,
) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(Unit) { createFocusNotificationChannel(context) }
    val bucket = remainingSeconds / 15
    LaunchedEffect(phase, bucket, taskTitle, permissionGranted) {
        if (!permissionGranted) return@LaunchedEffect
        when (phase) {
            FocusPhase.Focus -> notifyFocusTimer(
                context = context,
                title = "Focus running",
                text = "${formatSecondsShort(remainingSeconds)} left - $taskTitle",
                ongoing = true,
            )
            FocusPhase.FlowOvertime -> notifyFocusTimer(
                context = context,
                title = "Flow overtime",
                text = "${formatOvertimeSeconds(remainingSeconds)} flow - $taskTitle",
                ongoing = true,
            )
            FocusPhase.Break -> notifyFocusTimer(
                context = context,
                title = "Break running",
                text = "${formatSecondsShort(remainingSeconds)} left. No tracking.",
                ongoing = true,
            )
            else -> cancelFocusTimerNotification(context)
        }
    }
    LaunchedEffect(event?.id, permissionGranted) {
        val currentEvent = event ?: return@LaunchedEffect
        if (permissionGranted) {
            notifyFocusTimer(
                context = context,
                title = currentEvent.title,
                text = currentEvent.text,
                ongoing = false,
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { cancelFocusTimerNotification(context) }
    }
}

@Composable
private fun FocusCompletionCueEffect(event: FocusCompletionCueEvent?) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(event?.id, event?.kind) {
        event ?: return@LaunchedEffect
        launch { playFocusCompletionSound() }
        launch { playFocusCompletionHaptics(context) }
    }
}

private suspend fun playFocusCompletionSound() = withContext(Dispatchers.Default) {
    val samples = createFocusCompletionPcm()
    val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(FocusCueSampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
        .build()
    try {
        audioTrack.write(samples, 0, samples.size)
        audioTrack.play()
        delay(FocusCueSoundDurationMillis)
    } finally {
        audioTrack.stop()
        audioTrack.release()
    }
}

private suspend fun playFocusCompletionHaptics(context: Context) {
    val vibrator = context.focusVibrator() ?: return
    if (!vibrator.hasVibrator()) return
    repeat(FocusCueHapticPulseCount) { index ->
        vibrateFocusCuePulse(vibrator)
        delay(if (index == FocusCueHapticPulseCount - 1) {
            FocusCueHapticIntervalMillis - FocusCueHapticPulseMillis
        } else {
            FocusCueHapticIntervalMillis
        })
    }
}

private fun performFocusStartHaptic(view: View) {
    if (!view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

private fun Context.focusVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

private fun vibrateFocusCuePulse(vibrator: Vibrator) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                FocusCueHapticPulseMillis,
                FocusCueHapticAmplitude,
            ),
        )
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(FocusCueHapticPulseMillis)
    }
}

private fun createFocusCompletionPcm(): ShortArray {
    val sampleCount = (FocusCueSampleRate * FocusCueSoundDurationSeconds).toInt()
    val buffer = ShortArray(sampleCount)
    val notes = doubleArrayOf(392.0, 493.88, 587.33)
    for (i in buffer.indices) {
        val time = i.toDouble() / FocusCueSampleRate.toDouble()
        val fadeIn = (time / FocusCueFadeInSeconds).coerceIn(0.0, 1.0)
        val fadeOut = ((FocusCueSoundDurationSeconds - time) / FocusCueFadeOutSeconds).coerceIn(0.0, 1.0)
        val envelope = easeSine(fadeIn) * easeSine(fadeOut)
        val shimmer = (sin(2.0 * PI * notes[0] * time) * 0.55) +
            (sin(2.0 * PI * notes[1] * time) * 0.32) +
            (sin(2.0 * PI * notes[2] * time) * 0.18)
        val value = shimmer * envelope * FocusCueVolume
        buffer[i] = (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
    }
    return buffer
}

private fun easeSine(value: Double): Double =
    sin((value.coerceIn(0.0, 1.0) * PI) / 2.0)

private fun adaptFocusRound(
    report: FocusRoundReport,
    currentFocusMinutes: Int,
    currentBreakMinutes: Int,
    sessionFocusedSeconds: Int,
    cleanStreak: Int,
    interruptedStreak: Int,
    completedLeavesThisSession: Int,
): FocusAdaptation {
    val interruptionLoad = report.interruptionLoad()
    val phoneDownShare = if (report.focusedSeconds <= 0) {
        0f
    } else {
        report.phoneDownSeconds.toFloat() / report.focusedSeconds.toFloat()
    }
    val quietShare = if (report.focusedSeconds <= 0) {
        0f
    } else {
        report.quietSeconds.toFloat() / report.focusedSeconds.toFloat()
    }
    fun nextBreakFor(isCleanRound: Boolean): Int =
        adaptiveBreakMinutes(
            actualFocusSeconds = report.focusedSeconds,
            currentBreakMinutes = currentBreakMinutes,
            interruptionLoad = interruptionLoad,
            sessionFocusedSeconds = sessionFocusedSeconds,
            cleanStreak = cleanStreak,
            interruptedStreak = interruptedStreak,
            completedLeavesThisSession = completedLeavesThisSession,
            isCleanRound = isCleanRound,
        )

    val focusDecision = adaptiveFocusMinutes(
        report = report,
        currentFocusMinutes = currentFocusMinutes,
        interruptionLoad = interruptionLoad,
        quietShare = quietShare,
        phoneDownShare = phoneDownShare,
        faceDownAssistEnabled = report.faceDownAssistEnabled,
        sessionFocusedSeconds = sessionFocusedSeconds,
        cleanStreak = cleanStreak,
        interruptedStreak = interruptedStreak,
        completedLeavesThisSession = completedLeavesThisSession,
    )
    val nextFocus = focusDecision.nextFocusMinutes
    return FocusAdaptation(
        nextFocusMinutes = nextFocus,
        nextBreakMinutes = nextBreakFor(isCleanRound = focusDecision.isCleanRound),
        isCleanRound = focusDecision.isCleanRound,
        message = focusDecision.message,
        deepFocusMinutes = focusDecision.deepFocusMinutes,
    )
}

private fun initialFocusMinutes(task: GeneratedStudyBlock): Int =
    task.likelyStudyMinutes.coerceAtMost(InitialFocusMinutes).coerceAtLeast(MinFocusMinutes)

private fun adaptiveFocusMinutes(
    report: FocusRoundReport,
    currentFocusMinutes: Int,
    interruptionLoad: Int,
    quietShare: Float,
    phoneDownShare: Float,
    faceDownAssistEnabled: Boolean,
    sessionFocusedSeconds: Int,
    cleanStreak: Int,
    interruptedStreak: Int,
    completedLeavesThisSession: Int,
): FocusTimingDecision {
    val current = currentFocusMinutes.coerceIn(MinFocusMinutes, MaxFocusMinutes)
    val sessionMinutes = sessionFocusedSeconds / SecondsPerMinute
    val expectedSeconds = current * SecondsPerMinute
    val completionShare = if (expectedSeconds == 0) 0f else report.focusedSeconds.toFloat() / expectedSeconds
    val flowRatio = if (expectedSeconds == 0) {
        0f
    } else {
        report.flowOvertimeSeconds.toFloat() / expectedSeconds.toFloat()
    }

    fun earnedDeepSuggestion(): Int? {
        if (current < AutoFocusCeilingMinutes || current >= MaxFocusMinutes) return null
        if (sessionMinutes >= 75 || completedLeavesThisSession >= 4) return null
        val strongFlow = report.flowOvertimeSeconds >= 8 * SecondsPerMinute
        val settledEnough = quietShare >= 0.72f && cleanStreak >= 2
        val phoneDownEnough = faceDownAssistEnabled && phoneDownShare >= 0.65f && cleanStreak >= 1
        return if (strongFlow || settledEnough || phoneDownEnough) DeepFocusMinutes else null
    }

    if (!report.completed) {
        val shouldBackOff = completionShare < 0.65f || interruptedStreak >= 1
        val nextFocus = if (shouldBackOff) focusStep(current, -1) else current
        return FocusTimingDecision(
            nextFocusMinutes = nextFocus,
            isCleanRound = false,
            message = if (nextFocus < current) {
                "Stopped early. Step the timer down and protect the restart."
            } else {
                "Stopped early, but close enough to hold the timer steady. No fake shame economy here."
            },
        )
    }

    if (interruptionLoad >= 3) {
        val nextFocus = if (interruptedStreak >= 1 || report.awaySeconds >= 120) {
            focusStep(current, -1)
        } else {
            current
        }
        return FocusTimingDecision(
            nextFocusMinutes = nextFocus,
            isCleanRound = false,
            message = if (nextFocus < current) {
                "$interruptionLoad interruptions. Step down once, widen the break, then rebuild."
            } else {
                "$interruptionLoad interruptions. Hold the timer. The break does the recovery work."
            },
        )
    }

    if (interruptionLoad >= 1) {
        return FocusTimingDecision(
            nextFocusMinutes = current,
            isCleanRound = false,
            message = "$interruptionLoad interruption. Keep the timer stable and let the break absorb the turbulence.",
        )
    }

    val deepSuggestion = earnedDeepSuggestion()
    if (flowRatio >= 0.5f) {
        return FocusTimingDecision(
            nextFocusMinutes = current,
            isCleanRound = true,
            message = if (deepSuggestion != null) {
                "Good flow. Keep the next start easy, or try a ${formatMinutes(deepSuggestion)} deep round."
            } else {
                "Good flow. Hold the timer steady and let the break protect the next start."
            },
            deepFocusMinutes = deepSuggestion,
        )
    }

    val phoneDownBonus = if (faceDownAssistEnabled && phoneDownShare >= 0.65f) 1 else 0
    val settledScore = when {
        quietShare >= 0.72f -> 2
        quietShare >= 0.45f -> 1
        else -> 0
    } + phoneDownBonus
    val streakScore = when {
        cleanStreak >= 3 -> 2
        cleanStreak >= 1 -> 1
        else -> 0
    }
    val completionScore = if (completionShare >= 0.95f) 1 else 0
    val fatiguePenalty = when {
        sessionMinutes >= 75 -> 2
        sessionMinutes >= 45 -> 1
        else -> 0
    }
    val leafPenalty = if (completedLeavesThisSession >= 4) 1 else 0
    val longRoundPenalty = if (current >= 20 && cleanStreak < 2) 1 else 0
    val readinessScore = 1 + settledScore + streakScore + completionScore -
        fatiguePenalty - leafPenalty - longRoundPenalty

    if (deepSuggestion != null) {
        return FocusTimingDecision(
            nextFocusMinutes = current,
            isCleanRound = true,
            message = "Strong rhythm. Try a ${formatMinutes(deepSuggestion)} deep round if this leaf needs runway.",
            deepFocusMinutes = deepSuggestion,
        )
    }

    val step = when {
        current >= AutoFocusCeilingMinutes -> 0
        readinessScore >= 3 -> 1
        else -> 0
    }
    val nextFocus = automaticFocusStep(current, step)
    return FocusTimingDecision(
        nextFocusMinutes = nextFocus,
        isCleanRound = true,
        message = when {
            nextFocus > current -> "Clean round. Nudge the timer up; momentum gets a small promotion."
            sessionMinutes >= 45 -> "Clean round, but the day has mileage. Hold the timer and avoid cooking the battery."
            else -> "Clean round. Hold this length once more; consistency first, flex later."
        },
    )
}

private fun focusStep(current: Int, offset: Int): Int {
    val normalized = current.coerceIn(MinFocusMinutes, MaxFocusMinutes)
    if (normalized < FocusRamp.first()) {
        return if (offset > 0) FocusRamp.first() else normalized
    }
    val currentIndex = FocusRamp.indexOfFirst { it >= normalized }.takeIf { it >= 0 } ?: FocusRamp.lastIndex
    val targetIndex = (currentIndex + offset).coerceIn(0, FocusRamp.lastIndex)
    return FocusRamp[targetIndex].coerceIn(MinFocusMinutes, MaxFocusMinutes)
}

private fun automaticFocusStep(current: Int, offset: Int): Int =
    focusStep(current, offset).coerceAtMost(AutoFocusCeilingMinutes)

private fun manualFocusStep(current: Int, offset: Int): Int {
    if (offset < 0 && current <= FocusRamp.first()) return MinFocusMinutes
    if (offset > 0 && current < FocusRamp.first()) return FocusRamp.first()
    return focusStep(current, offset)
}

private fun flowExtensionMinutes(
    currentFocusMinutes: Int,
    flowOvertimeSeconds: Int,
    leafFocusedSeconds: Int,
    leafLikelyMinutes: Int,
    remainingBufferMinutes: Int,
    interruptionCount: Int,
    sessionFocusedSeconds: Int,
): Int {
    val minRunway = if (currentFocusMinutes <= MinFocusMinutes) MinFocusMinutes else 3
    val flowMinutes = flowOvertimeSeconds.toCeilMinutes()
    val leafFocusedMinutes = leafFocusedSeconds.toCeilMinutes()
    val leafRemainingMinutes = (leafLikelyMinutes - leafFocusedMinutes).coerceAtLeast(0)
    val sessionFocusedMinutes = sessionFocusedSeconds.toCeilMinutes()

    val baseRunway = when {
        currentFocusMinutes <= MinFocusMinutes -> MinFocusMinutes
        interruptionCount > 0 -> 3
        remainingBufferMinutes <= TightBreakBufferMinutes -> 3
        sessionFocusedMinutes >= 60 -> 3
        flowMinutes >= 8 && leafRemainingMinutes >= 12 && remainingBufferMinutes >= 10 -> 10
        else -> 5
    }
    val leafCap = when {
        leafLikelyMinutes <= 0 -> baseRunway
        leafRemainingMinutes <= 6 -> 3
        leafRemainingMinutes <= 12 -> baseRunway.coerceAtMost(5)
        else -> baseRunway
    }
    val bufferCap = when {
        remainingBufferMinutes <= TightBreakBufferMinutes -> 3
        remainingBufferMinutes <= 10 -> leafCap.coerceAtMost(5)
        else -> leafCap
    }
    return bufferCap.coerceIn(minRunway, 10)
}

private fun Int.toCeilMinutes(): Int =
    ((this.coerceAtLeast(0) + SecondsPerMinute - 1) / SecondsPerMinute)

private fun breakStep(current: Int, offset: Int): Int {
    val currentIndex = BreakRamp.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: BreakRamp.lastIndex
    val targetIndex = (currentIndex + offset).coerceIn(0, BreakRamp.lastIndex)
    return BreakRamp[targetIndex].coerceIn(MinBreakMinutes, MaxBreakMinutes)
}

private fun adaptiveBreakMinutes(
    actualFocusSeconds: Int,
    currentBreakMinutes: Int,
    interruptionLoad: Int,
    sessionFocusedSeconds: Int,
    cleanStreak: Int,
    interruptedStreak: Int,
    completedLeavesThisSession: Int,
    isCleanRound: Boolean,
): Int {
    val actualFocusMinutes = ((actualFocusSeconds + SecondsPerMinute - 1) / SecondsPerMinute)
        .coerceAtLeast(0)
    val sessionMinutes = sessionFocusedSeconds / SecondsPerMinute
    val baseBreak = when {
        actualFocusMinutes <= 12 -> 2
        actualFocusMinutes <= 20 -> 3
        actualFocusMinutes <= 30 -> 5
        actualFocusMinutes <= 45 -> 7
        actualFocusMinutes <= 60 -> 10
        else -> 15
    }
    val interruptionSteps = when {
        interruptionLoad >= 3 -> 2
        interruptionLoad >= 1 -> 1
        else -> 0
    }
    val fatigueSteps = when {
        sessionMinutes >= 75 -> 2
        sessionMinutes >= 45 -> 1
        completedLeavesThisSession >= 4 -> 1
        else -> 0
    }
    val repeatedInterruptionSteps = if (!isCleanRound && interruptedStreak >= 1) 1 else 0
    val target = breakStep(
        current = baseBreak,
        offset = interruptionSteps + fatigueSteps + repeatedInterruptionSteps,
    )

    return when {
        target > currentBreakMinutes -> {
            val maxStep = if (interruptionLoad >= 3 || fatigueSteps >= 2) 2 else 1
            breakStep(currentBreakMinutes, maxStep).coerceAtMost(target)
        }
        target < currentBreakMinutes && isCleanRound && cleanStreak >= 1 -> {
            breakStep(currentBreakMinutes, -1).coerceAtLeast(target)
        }
        else -> currentBreakMinutes
    }.coerceIn(MinBreakMinutes, MaxBreakMinutes)
}

@Composable
private fun phaseSubtitle(phase: FocusPhase): String = when (phase) {
    FocusPhase.Ready -> stringResource(R.string.focus_ready_subtitle)
    FocusPhase.Focus -> stringResource(R.string.focus_running_subtitle)
    FocusPhase.FlowOvertime -> stringResource(R.string.focus_running_subtitle)
    FocusPhase.Paused -> stringResource(R.string.focus_paused_subtitle)
    FocusPhase.BreakChoice -> stringResource(R.string.focus_break_choice_subtitle)
    FocusPhase.Break -> stringResource(R.string.focus_break_subtitle)
    FocusPhase.BreakEnded -> stringResource(R.string.focus_break_subtitle)
    FocusPhase.Summary -> stringResource(R.string.focus_summary_subtitle)
    FocusPhase.LeafDone -> stringResource(R.string.focus_leaf_done_subtitle)
}

private fun timerLabel(phase: FocusPhase, remainingSeconds: Int, focusMinutes: Int): String = when (phase) {
    FocusPhase.Ready -> formatSecondsShort(focusMinutes * SecondsPerMinute)
    FocusPhase.FlowOvertime,
    FocusPhase.BreakEnded,
        -> formatOvertimeSeconds(remainingSeconds)
    FocusPhase.Summary,
    FocusPhase.LeafDone,
        -> "Done"
    else -> formatSecondsShort(remainingSeconds)
}

private fun formatSecondsShort(seconds: Int): String {
    val minutes = seconds.coerceAtLeast(0) / SecondsPerMinute
    val remainder = seconds.coerceAtLeast(0) % SecondsPerMinute
    return "$minutes:${remainder.toString().padStart(2, '0')}"
}

private fun formatOvertimeSeconds(remainingSeconds: Int): String =
    "+${formatSecondsShort(-remainingSeconds)}"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun hasFocusNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun createFocusNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        FocusNotificationChannelId,
        "Focus mode",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = "Focus and break timer updates"
    }
    manager.createNotificationChannel(channel)
}

@SuppressLint("MissingPermission")
private fun notifyFocusTimer(
    context: Context,
    title: String,
    text: String,
    ongoing: Boolean,
) {
    if (!hasFocusNotificationPermission(context)) return
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pendingIntent = launchIntent?.let {
        PendingIntent.getActivity(
            context,
            0,
            it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    val notification = NotificationCompat.Builder(context, FocusNotificationChannelId)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(ongoing)
        .setOnlyAlertOnce(ongoing)
        .setAutoCancel(!ongoing)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .apply {
            if (pendingIntent != null) setContentIntent(pendingIntent)
        }
        .build()
    NotificationManagerCompat.from(context).notify(FocusNotificationId, notification)
}

private fun cancelFocusTimerNotification(context: Context) {
    NotificationManagerCompat.from(context).cancel(FocusNotificationId)
}

private enum class FocusPhase {
    Ready,
    Focus,
    FlowOvertime,
    Paused,
    BreakChoice,
    Break,
    BreakEnded,
    Summary,
    LeafDone,
}

private val RecoveryPhases = setOf(
    FocusPhase.Ready,
    FocusPhase.BreakEnded,
    FocusPhase.Summary,
    FocusPhase.LeafDone,
)
private val FocusTrackingPhases = setOf(FocusPhase.Focus, FocusPhase.FlowOvertime)
private val FocusStartGatePhases = setOf(FocusPhase.Ready, FocusPhase.Summary, FocusPhase.Paused)
private val BreakTrackingPhases = setOf(FocusPhase.Break, FocusPhase.BreakEnded)
private val TimerRunningPhases = FocusTrackingPhases + BreakTrackingPhases + FocusPhase.BreakEnded

private data class FocusPostureReading(
    val isSupported: Boolean,
    val isPhoneDown: Boolean,
)

private data class FocusRoundReport(
    val id: Long,
    val completed: Boolean,
    val focusedSeconds: Int,
    val flowOvertimeSeconds: Int = 0,
    val quietSeconds: Int,
    val screenInteractionCount: Int,
    val phoneDownSeconds: Int,
    val faceDownAssistEnabled: Boolean,
    val softGlanceCount: Int,
    val interruptionCount: Int,
    val awaySeconds: Int,
    val pauseSeconds: Int,
    val suggestion: String = "",
)

private data class FocusBreakImpact(
    val remainingBufferMinutes: Int,
    val alreadyOverMinutes: Int,
    val waitingTaskCount: Int,
    val waitingMinutes: Int,
)

private data class FocusBreakImpactMessage(
    val text: String,
    val isWarning: Boolean,
)

private fun breakImpactMessage(
    breakMinutes: Int,
    impact: FocusBreakImpact,
): FocusBreakImpactMessage {
    val projectedOverMinutes = impact.alreadyOverMinutes +
        (breakMinutes - impact.remainingBufferMinutes).coerceAtLeast(0)
    val bufferAfterBreak = (impact.remainingBufferMinutes - breakMinutes).coerceAtLeast(0)

    return when {
        projectedOverMinutes > 0 -> FocusBreakImpactMessage(
            text = "This may push today over by ${formatMinutes(projectedOverMinutes)}.",
            isWarning = true,
        )
        impact.waitingTaskCount > 0 -> FocusBreakImpactMessage(
            text = "${formatMinutes(bufferAfterBreak)} buffer left. ${impact.waitingTaskCount} leaves are already outside today's fit.",
            isWarning = true,
        )
        bufferAfterBreak <= TightBreakBufferMinutes -> FocusBreakImpactMessage(
            text = "${formatMinutes(bufferAfterBreak)} buffer left after this. Still doable, just snug.",
            isWarning = false,
        )
        else -> FocusBreakImpactMessage(
            text = "Leaves ${formatMinutes(bufferAfterBreak)} of today's buffer.",
            isWarning = false,
        )
    }
}

private fun FocusRoundReport.interruptionLoad(): Int =
    interruptionCount +
        (softGlanceCount / 3) +
        if (awaySeconds >= RhythmBreakSeconds) 1 else 0

private fun FocusRoundReport.toDaySummary(): FocusDayRoundSummary = FocusDayRoundSummary(
    completed = completed,
    focusedSeconds = focusedSeconds,
    flowOvertimeSeconds = flowOvertimeSeconds,
    quietSeconds = quietSeconds,
    screenInteractionCount = screenInteractionCount,
    phoneDownSeconds = phoneDownSeconds,
    faceDownAssistEnabled = faceDownAssistEnabled,
    softGlanceCount = softGlanceCount,
    interruptionCount = interruptionCount,
    awaySeconds = awaySeconds,
    pausedSeconds = pauseSeconds,
    suggestion = suggestion,
)

private fun FocusDayRoundSummary.toRoundReport(): FocusRoundReport = FocusRoundReport(
    id = 0L,
    completed = completed,
    focusedSeconds = focusedSeconds,
    flowOvertimeSeconds = flowOvertimeSeconds,
    quietSeconds = quietSeconds,
    screenInteractionCount = screenInteractionCount,
    phoneDownSeconds = phoneDownSeconds,
    faceDownAssistEnabled = faceDownAssistEnabled,
    softGlanceCount = softGlanceCount,
    interruptionCount = interruptionCount,
    awaySeconds = awaySeconds,
    pauseSeconds = pausedSeconds,
    suggestion = suggestion,
)

private data class FocusAdaptation(
    val nextFocusMinutes: Int,
    val nextBreakMinutes: Int,
    val isCleanRound: Boolean,
    val message: String,
    val deepFocusMinutes: Int? = null,
)

private data class FocusTimingDecision(
    val nextFocusMinutes: Int,
    val isCleanRound: Boolean,
    val message: String,
    val deepFocusMinutes: Int? = null,
)

private data class FocusNotificationEvent(
    val id: Long,
    val title: String,
    val text: String,
)

private data class FocusCompletionCueEvent(
    val id: Long,
    val kind: FocusCompletionCueKind,
)

private enum class FocusCompletionCueKind {
    FocusEnded,
    BreakEnded,
}

private const val SecondsPerMinute = 60
private const val InitialFocusMinutes = 10
private const val InitialBreakMinutes = 2
private const val MinFocusMinutes = 1
private const val AutoFocusCeilingMinutes = 25
private const val DeepFocusMinutes = 35
private const val MaxFocusMinutes = 45
private const val MinBreakMinutes = 2
private const val MaxBreakMinutes = 15
private const val TightBreakBufferMinutes = 5
private const val LongMomentumResetMillis = 60L * 60L * 1_000L
private const val RhythmBreakSeconds = 5 * 60
private const val InterruptionGraceMillis = 10_000L
private const val SoftGlanceMaxMillis = 20_000L
private const val QuietScreenThresholdMillis = 7_000L
private const val InteractionCountThrottleMillis = 1_000L
private const val FocusScreenBrightness = 0.08f
private const val FocusQuietTransitionMillis = 520
private const val FocusTimerRingAnimationMillis = 850
private const val FocusTopNoticeAnimationMillis = 260
private const val FocusTopNoticeDurationMillis = 4_500L
private const val FocusPhoneUpPauseGraceMillis = 1_000L
private const val FocusCueSoundDurationMillis = 3_000L
private const val FocusCueSoundDurationSeconds = 3.0
private const val FocusCueFadeInSeconds = 0.18
private const val FocusCueFadeOutSeconds = 1.3
private const val FocusCueSampleRate = 44_100
private const val FocusCueVolume = 0.11
private const val FocusCueHapticPulseCount = 4
private const val FocusCueHapticIntervalMillis = 1_000L
private const val FocusCueHapticPulseMillis = 42L
private const val FocusCueHapticAmplitude = 72
private const val FocusNotificationChannelId = "ren_focus_mode"
private const val FocusNotificationId = 9087
private const val FocusPhoneDownNotice =
    "Yep, it is that strict. Focus only counts while the screen faces down. Breaks start normally."
private const val FocusBaselineContentWidthDp = 342f
private const val FocusBaselineContentHeightDp = 724f
private const val FocusMinLayoutScale = 0.82f
private const val FocusBaselineTimerSizeDp = 332f
private const val FocusMinimumTimerSizeDp = 276f
private const val FocusBaselineHeadingOffsetDp = -242f
private const val FocusBaselinePanelOffsetDp = 272f
private const val FocusBaselineSuggestionOffsetDp = 196f
private const val FocusSuggestionGapDp = 30f
private const val FocusBaselineSessionLineBottomPaddingDp = 22f
private const val FocusMinimumSessionLineBottomPaddingDp = 10f
private const val FocusTightContentHeightDp = 680f
private const val FocusVeryTightContentHeightDp = 600f
private const val FocusBaselineBottomReserveDp = 72f
private const val FocusTightBottomReserveDp = 118f
private const val FocusMinimumPanelOffsetDp = 148f
private const val FocusTightHeadingCenterYDp = 86f
private const val FocusVeryTightHeadingCenterYDp = 72f
private val FocusRamp = listOf(10, 15, 20, 25, 35, 45)
private val BreakRamp = listOf(2, 3, 5, 7, 10, 15)
