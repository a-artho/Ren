package com.hci.ren.feature.studymap

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class TodaySessionDraftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "ren_today_session_drafts",
        Context.MODE_PRIVATE,
    )

    fun load(projectId: String, date: String): TodaySessionState? {
        if (projectId.isBlank() || date.toStudyCalendar() == null) return null
        return prefs.getString(key(projectId, date), null)
            ?.let(TodaySessionDraftJsonCodec::decode)
            ?.takeIf { it.date == date && !it.isEmpty }
    }

    fun save(projectId: String, session: TodaySessionState?) {
        if (projectId.isBlank()) return
        if (session == null || session.isEmpty || session.date.toStudyCalendar() == null) {
            session?.let { clear(projectId, it.date) }
            return
        }
        prefs.edit()
            .putString(key(projectId, session.date), TodaySessionDraftJsonCodec.encode(session))
            .apply()
    }

    fun clear(projectId: String, date: String) {
        if (projectId.isBlank() || date.toStudyCalendar() == null) return
        prefs.edit().remove(key(projectId, date)).apply()
    }

    fun clearProject(projectId: String) {
        if (projectId.isBlank()) return
        val prefix = "$projectId|"
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) return
        val edit = prefs.edit()
        keys.forEach(edit::remove)
        edit.apply()
    }

    private fun key(projectId: String, date: String) = "$projectId|$date"
}

internal object TodaySessionDraftJsonCodec {
    fun encode(session: TodaySessionState): String = JSONObject()
        .put("date", session.date)
        .apply {
            session.availableMinutes?.let { put("availableMinutes", it.coerceIn(0, MaxTodaySessionMinutes)) }
            put("movedLaterTaskIds", session.movedLaterTaskIds.toJsonArray())
            put("pulledInTaskIds", session.pulledInTaskIds.toJsonArray())
            put("doneTodayTaskIds", session.doneTodayTaskIds.toJsonArray())
            put("removedFromPlanTaskIds", session.removedFromPlanTaskIds.toJsonArray())
            put("focusSessions", JSONArray(session.focusSessions.map(FocusSessionRecord::toDraftJson)))
        }
        .toString()

    fun decode(raw: String): TodaySessionState? = runCatching {
        val json = JSONObject(raw)
        val date = json.optString("date")
        if (date.toStudyCalendar() == null) return@runCatching null
        val session = TodaySessionState(
            date = date,
            availableMinutes = if (json.has("availableMinutes")) {
                json.optInt("availableMinutes", -1).takeIf { it in 0..MaxTodaySessionMinutes }
            } else {
                null
            },
            movedLaterTaskIds = json.optJSONArray("movedLaterTaskIds").taskIdSet(),
            pulledInTaskIds = json.optJSONArray("pulledInTaskIds").taskIdSet(),
            doneTodayTaskIds = json.optJSONArray("doneTodayTaskIds").taskIdSet(),
            removedFromPlanTaskIds = json.optJSONArray("removedFromPlanTaskIds").taskIdSet(),
            focusSessions = json.optJSONArray("focusSessions")
                ?.objects()
                ?.mapNotNull(JSONObject::toDraftFocusSessionRecord)
                .orEmpty(),
        )
        session.takeUnless { it.isEmpty }
    }.getOrNull()
}

private fun Set<String>.toJsonArray() = JSONArray(toList().sorted())

private fun JSONArray?.taskIdSet(): Set<String> = this
    ?.strings()
    ?.filter { it.isNotBlank() }
    ?.toSet()
    .orEmpty()

private fun FocusSessionRecord.toDraftJson() = JSONObject()
    .put("taskId", taskId)
    .put("plannedFocusMinutes", plannedFocusMinutes)
    .put("plannedFocusSeconds", plannedFocusSeconds)
    .put("plannedBreakMinutes", plannedBreakMinutes)
    .put("focusSeconds", focusSeconds)
    .put("flowOvertimeSeconds", flowOvertimeSeconds)
    .put("breakSeconds", breakSeconds)
    .put("awaySeconds", awaySeconds)
    .put("interruptionCount", interruptionCount)
    .put("outcome", outcome.name)
    .put("endedAtMillis", endedAtMillis)

private fun JSONObject.toDraftFocusSessionRecord(): FocusSessionRecord? {
    val record = FocusSessionRecord(
        taskId = optString("taskId"),
        plannedFocusMinutes = optInt("plannedFocusMinutes", 0).coerceAtLeast(0),
        plannedFocusSeconds = optInt(
            "plannedFocusSeconds",
            optInt("plannedFocusMinutes", 0).coerceAtLeast(0) * DraftFocusRecordSecondsPerMinute,
        ).coerceAtLeast(0),
        plannedBreakMinutes = optInt("plannedBreakMinutes", 0).coerceAtLeast(0),
        focusSeconds = optInt("focusSeconds", 0).coerceAtLeast(0),
        flowOvertimeSeconds = optInt("flowOvertimeSeconds", 0).coerceAtLeast(0),
        breakSeconds = optInt("breakSeconds", 0).coerceAtLeast(0),
        awaySeconds = optInt("awaySeconds", 0).coerceAtLeast(0),
        interruptionCount = optInt("interruptionCount", 0).coerceAtLeast(0),
        outcome = optString("outcome").enumOrDraft(FocusSessionOutcome.FocusRoundEnded),
        endedAtMillis = optLong("endedAtMillis", 0L).coerceAtLeast(0L),
    )
    return record.takeIf { it.taskId.isNotBlank() && it.hasTrackedTime }
}

private inline fun <reified T : Enum<T>> String.enumOrDraft(default: T): T =
    runCatching { enumValueOf<T>(this) }.getOrDefault(default)

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

private fun JSONArray.strings() = (0 until length()).map { getString(it) }

private const val DraftFocusRecordSecondsPerMinute = 60
