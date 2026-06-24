package com.hci.ren.feature.plangeneration

import android.content.ContentResolver
import android.net.Uri
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import java.util.UUID

class PlanApiRepository(
    private val contentResolver: ContentResolver,
    private val baseUrl: String = if (
        android.os.Build.FINGERPRINT.startsWith("generic") ||
        android.os.Build.MODEL.contains("google_sdk") ||
        android.os.Build.HARDWARE.contains("goldfish") ||
        android.os.Build.HARDWARE.contains("ranchu")
    ) {
        "http://10.0.2.2:8000"
    } else {
        "http://127.0.0.1:8000"
    },
) {
    fun uploadDocument(uri: Uri, requestId: String): String {
        val boundary = "Ren-${UUID.randomUUID()}"
        val connection = open("/documents", "POST").apply {
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setChunkedStreamingMode(64 * 1024)
            doOutput = true
        }
        return try {
            connection.outputStream.buffered(64 * 1024).use { output ->
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n$requestId\r\n".toByteArray())
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.pdf\"\r\nContent-Type: application/pdf\r\n\r\n".toByteArray())
                contentResolver.openInputStream(uri)?.use { it.copyTo(output, 64 * 1024) }
                    ?: error("The selected PDF is no longer accessible")
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            responseJson(connection).getString("documentId")
        } finally {
            connection.disconnect()
        }
    }

    fun createPlan(documentId: String, submission: PlanSetupSubmission, requestId: String): String {
        val setup = JSONObject()
            .put("goal", submission.goal.name)
            .put("deadline", submission.deadline.name)
            .put("deadlineDate", submission.deadlineDate)
            .put("dailyStudyMinutes", submission.dailyStudyMinutes)
            .put("studyDays", JSONArray(submission.studyDays.sortedBy { it.ordinal }.map { it.name }))
        val body = JSONObject().put("documentId", documentId).put("requestId", requestId).put("setup", setup)
        return jsonRequest("/plans", "POST", body).getString("planId")
    }

    fun status(planId: String): PlanStatus = PlanStatus.fromWire(
        jsonRequest("/plans/$planId/status", "GET").getString("status"),
    )

    fun plan(planId: String): GeneratedStudyPlan {
        val json = jsonRequest("/plans/$planId", "GET")
        val topics = json.getJSONArray("topics").objects().map {
            StudyTopic(it.getString("id"), it.getString("title"), it.getInt("order"))
        }.sortedBy { it.order }
        val blocks = json.getJSONArray("blocks").objects().map {
            GeneratedStudyBlock(
                it.getString("id"), it.getString("title"), it.getInt("order"),
                it.getInt("durationMinutes").coerceAtLeast(1), it.getString("instructions"),
                it.getJSONArray("topicIds").strings(),
                it.optInt("minimumUsefulMinutes", 10).coerceAtLeast(1),
                it.optString("priority", "MEDIUM").toTaskPriority(),
                it.optString("taskType", "REVIEW").toStudyTaskType(),
                it.optString("priorityReason", "Supports the study goal"),
                it.optBoolean("isSkippable", true),
                status = it.optString("status", "NOT_STARTED").toTaskStatus(),
                scheduledDate = it.optString("scheduledDate").takeUnless { value -> value.isBlank() || value == "null" },
                dependencies = it.optJSONArray("dependencies")?.strings().orEmpty(),
                isOptional = it.optBoolean("isOptional", false),
                isExcluded = it.optBoolean("isExcluded", false),
            )
        }.sortedBy { it.order }
        val title = if (json.has("title") && !json.isNull("title")) {
            json.optString("title", "").takeIf { it.isNotBlank() }
        } else null
        return GeneratedStudyPlan(planId, topics, blocks, json.getInt("totalEstimatedMinutes"),
            projectName = title ?: DEFAULT_PROJECT_NAME)
    }

    fun cancelPlan(planId: String) {
        val connection = open("/plans/$planId/cancel", "POST")
        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }
            if (responseCode !in 200..299) throw PlanApiException(responseCode)
        } finally {
            connection.disconnect()
        }
    }

    private fun jsonRequest(path: String, method: String, body: JSONObject? = null): JSONObject {
        val connection = open(path, method)
        return try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            }
            responseJson(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, method: String) = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 15_000
        readTimeout = 30_000
    }

    private fun responseJson(connection: HttpURLConnection): JSONObject {
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) throw PlanApiException(responseCode)
        return JSONObject(text)
    }
}

internal class PlanApiException(
    val statusCode: Int,
) : IOException("Server request failed ($statusCode)")

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strings() = (0 until length()).map { getString(it) }
private fun String.toTaskPriority() = runCatching { TaskPriority.valueOf(lowercase().replaceFirstChar(Char::uppercase)) }.getOrDefault(TaskPriority.Medium)
private fun String.toStudyTaskType() = runCatching {
    StudyTaskType.valueOf(lowercase().split('_').joinToString("") { it.replaceFirstChar(Char::uppercase) })
}.getOrDefault(StudyTaskType.Custom)

private fun String.toTaskStatus() = runCatching {
    StudyTaskStatus.valueOf(lowercase().split('_').joinToString("") { it.replaceFirstChar(Char::uppercase) })
}.getOrDefault(StudyTaskStatus.NotStarted)

