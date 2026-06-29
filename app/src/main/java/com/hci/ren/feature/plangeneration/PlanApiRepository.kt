package com.hci.ren.feature.plangeneration

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
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
        val filename = contentResolver.displayName(uri).multipartSafeFilename()
        val connection = open("/documents", "POST").apply {
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setChunkedStreamingMode(64 * 1024)
            doOutput = true
        }
        return try {
            connection.outputStream.buffered(64 * 1024).use { output ->
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n$requestId\r\n".toByteArray())
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\nContent-Type: application/pdf\r\n\r\n".toByteArray())
                contentResolver.openInputStream(uri)?.use { it.copyTo(output, 64 * 1024) }
                    ?: error("The selected PDF is no longer accessible")
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            responseJson(connection).getString("documentId")
        } finally {
            connection.disconnect()
        }
    }

    fun uploadDocuments(
        uris: List<Uri>,
        requestId: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): List<String> {
        val total = uris.size
        return uris.mapIndexed { index, uri ->
            onProgress(index + 1, total)
            uploadDocument(uri, "${requestId}-${index}")
        }
    }

    fun createPlan(documentIds: List<String>, submission: PlanSetupSubmission, requestId: String): String {
        val setup = JSONObject()
            .put("goal", submission.goal.name)
            .put("planTitle", submission.planTitle)
            .put("deadline", submission.deadline.name)
            .put("deadlineDate", submission.deadlineDate)
            .put("dailyStudyMinutes", submission.dailyStudyMinutes)
            .put("studyDays", JSONArray(submission.studyDays.sortedBy { it.ordinal }.map { it.name }))
        val body = JSONObject()
            .put("documentIds", JSONArray(documentIds))
            .put("requestId", requestId)
            .put("setup", setup)
        return jsonRequest("/plans", "POST", body).getString("planId")
    }

    fun status(planId: String): PlanStatus = PlanStatus.fromWire(
        jsonRequest("/plans/$planId/status", "GET").getString("status"),
    )

    fun plan(planId: String): GeneratedStudyPlan {
        val json = jsonRequest("/plans/$planId", "GET")
        val sourceDocuments = json.optJSONArray("sourceDocuments")?.objects()?.map {
            StudySourceDocument(
                id = it.getString("id"),
                filename = it.getString("filename"),
                order = it.getInt("order"),
                pageCount = it.optInt("pageCount").takeIf { value -> value > 0 },
                uploadDocumentId = it.optString("uploadDocumentId").takeUnless { value -> value.isBlank() || value == "null" },
            )
        }.orEmpty().sortedBy { it.order }
        val topics = json.getJSONArray("topics").objects().map {
            StudyTopic(it.getString("id"), it.getString("title"), it.getInt("order"))
        }.sortedBy { it.order }
        val blocks = json.getJSONArray("blocks").objects().map {
            val duration = when {
                it.has("durationMinutes") && !it.isNull("durationMinutes") -> it.getInt("durationMinutes")
                else -> it.optInt("estimatedMinutes", 1)
            }.coerceAtLeast(1)
            GeneratedStudyBlock(
                id = it.getString("id"),
                title = it.getString("title"),
                order = it.getInt("order"),
                durationMinutes = duration,
                effortMinMinutes = it.optInt("effortMinMinutes", duration).coerceAtLeast(1),
                effortLikelyMinutes = it.optInt("effortLikelyMinutes", duration).coerceAtLeast(1),
                effortMaxMinutes = it.optInt("effortMaxMinutes", duration).coerceAtLeast(1),
                instructions = it.getString("instructions"),
                topicIds = it.getJSONArray("topicIds").strings(),
                minimumUsefulMinutes = it.optInt("minimumUsefulMinutes", 10).coerceAtLeast(1),
                taskType = it.optString("taskType", "REVIEW").toStudyTaskType(),
                status = it.optString("status", "NOT_STARTED").toTaskStatus(),
                scheduledDate = it.optString("scheduledDate").takeUnless { value -> value.isBlank() || value == "null" },
                dependencies = it.optJSONArray("dependencies")?.strings().orEmpty(),
                sourceRefs = it.optJSONArray("sourceRefs")?.objects()?.map(JSONObject::toStudySourceRef).orEmpty(),
                difficulty = it.optString("difficulty", "STANDARD").toStudyBlockDifficulty(),
                difficultyScore = it.optInt("difficultyScore").takeIf { value -> value > 0 },
                densityScore = it.optInt("densityScore").takeIf { value -> value > 0 },
                productionDemandScore = it.optInt("productionDemandScore").takeIf { value -> value > 0 },
                estimateConfidence = it.optString("estimateConfidence", "MEDIUM").toEstimateConfidence(),
                estimatedMinutes = it.optInt("estimatedMinutes", duration).coerceAtLeast(1),
                completionCriteria = it.optJSONArray("completionCriteria")?.strings().orEmpty(),
                splitAllowed = it.optBoolean("splitAllowed", true),
                continuityGroup = it.optString("continuityGroup").takeUnless { value -> value.isBlank() || value == "null" },
            )
        }.sortedBy { it.order }
        val title = if (json.has("title") && !json.isNull("title")) {
            json.optString("title", "").takeIf { it.isNotBlank() }
        } else null
        return GeneratedStudyPlan(
            id = planId,
            topics = topics,
            blocks = blocks,
            totalEstimatedMinutes = json.getInt("totalEstimatedMinutes"),
            projectName = title ?: DEFAULT_PROJECT_NAME,
            planVersion = json.optInt("planVersion", 1),
            sourceDocuments = sourceDocuments,
            extractionWarnings = json.optJSONArray("extractionWarnings")?.objects()?.map(JSONObject::toExtractionWarning).orEmpty(),
        )
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

    fun deleteDocument(documentId: String) {
        val connection = open("/documents/$documentId", "DELETE")
        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }
            if (responseCode !in 200..299 && responseCode != 404) throw PlanApiException(responseCode)
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
private fun ContentResolver.displayName(uri: Uri): String {
    return query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (columnIndex >= 0 && cursor.moveToFirst()) cursor.getString(columnIndex) else null
    }?.takeIf { it.isNotBlank() } ?: "document.pdf"
}
private fun String.multipartSafeFilename() = replace("\\", "_")
    .replace("\"", "_")
    .replace("\r", "_")
    .replace("\n", "_")
private fun JSONObject.toStudySourceRef() = StudySourceRef(
    documentId = getString("documentId"),
    startPage = optInt("startPage").takeIf { it > 0 },
    endPage = optInt("endPage").takeIf { it > 0 },
    sectionTitle = optString("sectionTitle").takeUnless { it.isBlank() || it == "null" },
    materialGroupTitle = optString("materialGroupTitle").takeUnless { it.isBlank() || it == "null" },
)

private fun JSONObject.toExtractionWarning() = ExtractionWarning(
    type = optString("type"),
    message = optString("message"),
    blockId = optString("blockId").takeUnless { it.isBlank() || it == "null" },
    documentId = optString("documentId").takeUnless { it.isBlank() || it == "null" },
    startPage = optInt("startPage").takeIf { it > 0 },
    endPage = optInt("endPage").takeIf { it > 0 },
)

private fun String.toStudyTaskType(): StudyTaskType {
    val normalized = wireEnumName()
    if (normalized == "Skim") return StudyTaskType.Reading
    return runCatching { StudyTaskType.valueOf(normalized) }.getOrDefault(StudyTaskType.Custom)
}

private fun String.toTaskStatus() = runCatching {
    StudyTaskStatus.valueOf(wireEnumName())
}.getOrDefault(StudyTaskStatus.NotStarted)

private fun String.toStudyBlockDifficulty() = runCatching {
    StudyBlockDifficulty.valueOf(wireEnumName())
}.getOrDefault(StudyBlockDifficulty.Standard)

private fun String.toEstimateConfidence() = runCatching {
    EstimateConfidence.valueOf(wireEnumName())
}.getOrDefault(EstimateConfidence.Medium)

private fun String.wireEnumName() = trim()
    .lowercase()
    .split('_', '-', ' ')
    .filter { it.isNotBlank() }
    .joinToString("") { part -> part.replaceFirstChar(Char::uppercase) }
