package com.hci.ren.feature.plangeneration

import android.content.ContentResolver
import android.net.Uri
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    fun uploadDocument(uri: Uri): String {
        val boundary = "Ren-${UUID.randomUUID()}"
        val connection = open("/documents", "POST").apply {
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            doOutput = true
        }
        connection.outputStream.buffered().use { output ->
            output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.pdf\"\r\nContent-Type: application/pdf\r\n\r\n".toByteArray())
            contentResolver.openInputStream(uri)?.use { it.copyTo(output) }
                ?: error("The selected PDF is no longer accessible")
            output.write("\r\n--$boundary--\r\n".toByteArray())
        }
        return responseJson(connection).getString("documentId")
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
                it.getInt("durationMinutes"), it.getString("instructions"),
                it.getJSONArray("topicIds").strings(),
            )
        }.sortedBy { it.order }
        return GeneratedStudyPlan(planId, topics, blocks, json.getInt("totalEstimatedMinutes"))
    }

    fun cancelPlan(planId: String) {
        val connection = open("/plans/$planId/cancel", "POST")
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) error("Server request failed ($responseCode): $text")
    }

    private fun jsonRequest(path: String, method: String, body: JSONObject? = null): JSONObject {
        val connection = open(path, method)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
        }
        return responseJson(connection)
    }

    private fun open(path: String, method: String) = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 15_000
        readTimeout = 30_000
    }

    private fun responseJson(connection: HttpURLConnection): JSONObject {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) error("Server request failed (${connection.responseCode}): $text")
        return JSONObject(text)
    }
}

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strings() = (0 until length()).map { getString(it) }

