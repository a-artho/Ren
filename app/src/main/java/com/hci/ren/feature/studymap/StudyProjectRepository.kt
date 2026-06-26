package com.hci.ren.feature.studymap

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.ExtractionWarning
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.EstimateConfidence
import com.hci.ren.feature.plangeneration.StudyBlockDifficulty
import com.hci.ren.feature.plangeneration.StudySourceDocument
import com.hci.ren.feature.plangeneration.StudySourceRef
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.feature.plangeneration.StudyTopic
import java.util.Calendar
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class StudyProject(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deadlineAtMillis: Long?,
    val plan: GeneratedStudyPlan,
    val preferences: PlanSetupSubmission,
    val dailyMinutesOverride: Int? = null,
    val acceptedTightPlan: Boolean = false,
)

private const val ActiveProjectSlot = "active"

@Entity(tableName = "active_study_project")
data class StudyProjectEntity(
    @PrimaryKey val slot: String = ActiveProjectSlot,
    val projectId: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deadlineAtMillis: Long?,
    val planJson: String,
    val preferencesJson: String,
    val dailyMinutesOverride: Int?,
    val acceptedTightPlan: Boolean,
)

@Dao
interface StudyProjectDao {
    @Query("SELECT * FROM active_study_project WHERE slot = 'active' LIMIT 1")
    suspend fun getActiveEntity(): StudyProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: StudyProjectEntity)

    @Query("DELETE FROM active_study_project")
    suspend fun deleteAll()

    @Transaction
    suspend fun getActive(): StudyProjectEntity? = getActiveEntity()

    @Transaction
    suspend fun replaceActive(project: StudyProjectEntity) {
        deleteAll()
        upsert(project.copy(slot = ActiveProjectSlot))
    }
}

@Database(entities = [StudyProjectEntity::class], version = 2, exportSchema = true)
abstract class StudyProjectDatabase : RoomDatabase() {
    abstract fun studyProjectDao(): StudyProjectDao

    companion object {
        @Volatile private var instance: StudyProjectDatabase? = null

        fun getInstance(context: Context): StudyProjectDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StudyProjectDatabase::class.java,
                "ren-study-projects.db",
            )
                .addMigrations(DropLegacyStudyProjectsMigration)
                .build()
                .also { instance = it }
        }
    }
}

private val DropLegacyStudyProjectsMigration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `study_projects`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `active_study_project` (
                `slot` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `createdAtMillis` INTEGER NOT NULL,
                `updatedAtMillis` INTEGER NOT NULL,
                `deadlineAtMillis` INTEGER,
                `planJson` TEXT NOT NULL,
                `preferencesJson` TEXT NOT NULL,
                `dailyMinutesOverride` INTEGER,
                `acceptedTightPlan` INTEGER NOT NULL,
                PRIMARY KEY(`slot`)
            )
            """.trimIndent(),
        )
    }
}

class StudyProjectRepository(private val dao: StudyProjectDao) {
    suspend fun getActive(): StudyProject? = dao.getActive()?.let(StudyProjectJsonCodec::decode)

    suspend fun upsert(project: StudyProject) = dao.replaceActive(StudyProjectJsonCodec.encode(project))

    suspend fun deleteActive() = dao.deleteAll()

    suspend fun saveGeneratedPlan(plan: GeneratedStudyPlan, preferences: PlanSetupSubmission) {
        val generated = newStudyProject(plan, preferences)
        dao.replaceActive(StudyProjectJsonCodec.encode(generated))
    }

    companion object {
        fun create(context: Context) = StudyProjectRepository(
            StudyProjectDatabase.getInstance(context).studyProjectDao(),
        )
    }
}

internal object StudyProjectJsonCodec {
    fun encode(project: StudyProject) = StudyProjectEntity(
        projectId = project.id,
        title = project.title.safeStudyProjectTitle(),
        createdAtMillis = project.createdAtMillis,
        updatedAtMillis = project.updatedAtMillis,
        deadlineAtMillis = project.deadlineAtMillis,
        planJson = project.plan.toJson().toString(),
        preferencesJson = project.preferences.toPersistedJson().toString(),
        dailyMinutesOverride = project.dailyMinutesOverride,
        acceptedTightPlan = project.acceptedTightPlan,
    )

    fun decode(entity: StudyProjectEntity): StudyProject {
        val plan = JSONObject(entity.planJson).toPlan()
        return StudyProject(
            id = entity.projectId,
            title = entity.title.safeStudyProjectTitle(),
            createdAtMillis = entity.createdAtMillis,
            updatedAtMillis = entity.updatedAtMillis,
            deadlineAtMillis = entity.deadlineAtMillis,
            plan = plan.copy(projectName = entity.title.safeStudyProjectTitle()),
            preferences = JSONObject(entity.preferencesJson).toSubmission(),
            dailyMinutesOverride = entity.dailyMinutesOverride,
            acceptedTightPlan = entity.acceptedTightPlan,
        )
    }
}

internal fun newStudyProject(
    plan: GeneratedStudyPlan,
    preferences: PlanSetupSubmission,
    nowMillis: Long = System.currentTimeMillis(),
): StudyProject {
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val absoluteDeadline = deadlineDate(preferences, now)?.timeInMillis
    val persistedPreferences = preferences.copy(
        documentUris = emptyList(),
        deadline = StudyDeadline.ChooseDate,
        deadlineDate = absoluteDeadline?.let {
            Calendar.getInstance().apply { timeInMillis = it }.toStudyDate()
        } ?: preferences.deadlineDate,
    )
    val title = preferences.planTitle.trim().ifBlank { plan.projectName }.safeStudyProjectTitle()
    return StudyProject(
        id = plan.id,
        title = title,
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis,
        deadlineAtMillis = absoluteDeadline,
        plan = plan.copy(projectName = title),
        preferences = persistedPreferences,
    )
}

internal fun String?.safeStudyProjectTitle(): String =
    this?.trim().orEmpty().ifBlank { "Untitled plan" }.toStudyProjectTitleCase()

private fun String.toStudyProjectTitleCase(locale: Locale = Locale.getDefault()): String =
    trim()
        .replace(Regex("\\s+"), " ")
        .split(" ")
        .joinToString(" ") { word -> word.toStudyProjectTitleCaseWord(locale) }

private fun String.toStudyProjectTitleCaseWord(locale: Locale): String =
    Regex("[\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+")
        .findAll(this)
        .joinToString("") { match ->
            val part = match.value
            val letters = part.filter { it.isLetter() }
            when {
                letters.isEmpty() -> part
                letters.all { it.isUpperCase() } -> part
                else -> part.lowercase(locale).replaceFirstChar { first ->
                    if (first.isLowerCase()) first.titlecase(locale) else first.toString()
                }
            }
        }

private fun GeneratedStudyPlan.toJson() = JSONObject()
    .put("id", id)
    .put("planVersion", planVersion)
    .put("projectName", projectName)
    .put("totalEstimatedMinutes", totalEstimatedMinutes)
    .put("sourceDocuments", JSONArray(sourceDocuments.map(StudySourceDocument::toJson)))
    .put("extractionWarnings", JSONArray(extractionWarnings.map(ExtractionWarning::toJson)))
    .put("topics", JSONArray(topics.map { topic ->
        JSONObject().put("id", topic.id).put("title", topic.title).put("order", topic.order)
    }))
    .put("blocks", JSONArray(blocks.map(GeneratedStudyBlock::toJson)))

private fun StudySourceDocument.toJson() = JSONObject()
    .put("id", id)
    .put("filename", filename)
    .put("order", order)
    .put("pageCount", pageCount)
    .put("uploadDocumentId", uploadDocumentId)

private fun GeneratedStudyBlock.toJson() = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("order", order)
    .put("durationMinutes", durationMinutes)
    .put("effortMinMinutes", effortMinMinutes)
    .put("effortLikelyMinutes", effortLikelyMinutes)
    .put("effortMaxMinutes", effortMaxMinutes)
    .put("estimatedMinutes", estimatedMinutes)
    .put("instructions", instructions)
    .put("topicIds", JSONArray(topicIds))
    .put("minimumUsefulMinutes", minimumUsefulMinutes)
    .put("taskType", taskType.name)
    .put("status", status.name)
    .put("scheduledDate", scheduledDate)
    .put("dependencies", JSONArray(dependencies))
    .put("sourceRefs", JSONArray(sourceRefs.map(StudySourceRef::toJson)))
    .put("difficulty", difficulty.name)
    .put("difficultyScore", difficultyScore)
    .put("densityScore", densityScore)
    .put("productionDemandScore", productionDemandScore)
    .put("estimateConfidence", estimateConfidence.name)
    .put("completionCriteria", JSONArray(completionCriteria))
    .put("splitAllowed", splitAllowed)
    .put("continuityGroup", continuityGroup)

private fun StudySourceRef.toJson() = JSONObject()
    .put("documentId", documentId)
    .put("startPage", startPage)
    .put("endPage", endPage)
    .put("sectionTitle", sectionTitle)

private fun ExtractionWarning.toJson() = JSONObject()
    .put("type", type)
    .put("message", message)
    .put("blockId", blockId)
    .put("documentId", documentId)
    .put("startPage", startPage)
    .put("endPage", endPage)

private fun JSONObject.toPlan(): GeneratedStudyPlan = GeneratedStudyPlan(
    id = getString("id"),
    topics = getJSONArray("topics").objects().map {
        StudyTopic(it.getString("id"), it.getString("title"), it.getInt("order"))
    }.sortedBy { it.order },
    blocks = getJSONArray("blocks").objects().map(JSONObject::toBlock).sortedBy { it.order },
    totalEstimatedMinutes = optInt("totalEstimatedMinutes"),
    projectName = optString("projectName").safeStudyProjectTitle(),
    planVersion = optInt("planVersion", 1),
    sourceDocuments = optJSONArray("sourceDocuments")?.objects()?.map(JSONObject::toSourceDocument).orEmpty().sortedBy { it.order },
    extractionWarnings = optJSONArray("extractionWarnings")?.objects()?.map(JSONObject::toExtractionWarning).orEmpty(),
)

private fun JSONObject.toSourceDocument() = StudySourceDocument(
    id = getString("id"),
    filename = getString("filename"),
    order = getInt("order"),
    pageCount = optInt("pageCount").takeIf { it > 0 },
    uploadDocumentId = optString("uploadDocumentId").takeUnless { it.isBlank() || it == "null" },
)

private fun JSONObject.toBlock() = GeneratedStudyBlock(
    id = getString("id"),
    title = getString("title"),
    order = getInt("order"),
    durationMinutes = optInt("durationMinutes", 1).coerceAtLeast(1),
    effortMinMinutes = optInt("effortMinMinutes", optInt("durationMinutes", 1)).coerceAtLeast(1),
    effortLikelyMinutes = optInt("effortLikelyMinutes", optInt("durationMinutes", 1)).coerceAtLeast(1),
    effortMaxMinutes = optInt("effortMaxMinutes", optInt("durationMinutes", 1)).coerceAtLeast(1),
    instructions = optString("instructions"),
    topicIds = getJSONArray("topicIds").strings(),
    minimumUsefulMinutes = optInt("minimumUsefulMinutes", 10).coerceAtLeast(1),
    taskType = optString("taskType").studyTaskTypeOrDefault(StudyTaskType.Review),
    status = optString("status").enumOr(StudyTaskStatus.NotStarted),
    scheduledDate = optString("scheduledDate").takeUnless { it.isBlank() || it == "null" },
    dependencies = optJSONArray("dependencies")?.strings().orEmpty(),
    sourceRefs = optJSONArray("sourceRefs")?.objects()?.map(JSONObject::toSourceRef).orEmpty(),
    difficulty = optString("difficulty", "Standard").enumOr(StudyBlockDifficulty.Standard),
    difficultyScore = optInt("difficultyScore").takeIf { it > 0 },
    densityScore = optInt("densityScore").takeIf { it > 0 },
    productionDemandScore = optInt("productionDemandScore").takeIf { it > 0 },
    estimateConfidence = optString("estimateConfidence", "Medium").enumOr(EstimateConfidence.Medium),
    estimatedMinutes = optInt("estimatedMinutes", optInt("durationMinutes", 1)).coerceAtLeast(1),
    completionCriteria = optJSONArray("completionCriteria")?.strings().orEmpty(),
    splitAllowed = optBoolean("splitAllowed", true),
    continuityGroup = optString("continuityGroup").takeUnless { it.isBlank() || it == "null" },
)

private fun JSONObject.toSourceRef() = StudySourceRef(
    documentId = getString("documentId"),
    startPage = optInt("startPage").takeIf { it > 0 },
    endPage = optInt("endPage").takeIf { it > 0 },
    sectionTitle = optString("sectionTitle").takeUnless { it.isBlank() || it == "null" },
)

private fun JSONObject.toExtractionWarning() = ExtractionWarning(
    type = optString("type"),
    message = optString("message"),
    blockId = optString("blockId").takeUnless { it.isBlank() || it == "null" },
    documentId = optString("documentId").takeUnless { it.isBlank() || it == "null" },
    startPage = optInt("startPage").takeIf { it > 0 },
    endPage = optInt("endPage").takeIf { it > 0 },
)

private fun PlanSetupSubmission.toPersistedJson() = JSONObject()
    .put("goal", goal.name)
    .put("planTitle", planTitle)
    .put("deadline", deadline.name)
    .put("deadlineDate", deadlineDate)
    .put("dailyStudyMinutes", dailyStudyMinutes)
    .put("studyDays", JSONArray(studyDays.map { it.name }))

private fun JSONObject.toSubmission() = PlanSetupSubmission(
    documentUris = emptyList(),
    goal = getString("goal").enumOr(StudyGoal.PrepareForExam),
    deadline = getString("deadline").enumOr(StudyDeadline.ChooseDate),
    deadlineDate = optString("deadlineDate").takeUnless { it.isBlank() || it == "null" },
    dailyStudyMinutes = optInt("dailyStudyMinutes", 60).coerceIn(1, 1_440),
    studyDays = getJSONArray("studyDays").strings().mapNotNull { runCatching { StudyDay.valueOf(it) }.getOrNull() }.toSet()
        .ifEmpty { StudyDay.entries.toSet() },
    planTitle = optString("planTitle").takeUnless { it.isBlank() || it == "null" }.orEmpty(),
)

private inline fun <reified T : Enum<T>> String.enumOr(default: T): T =
    runCatching { enumValueOf<T>(this) }
        .recoverCatching { enumValueOf<T>(wireEnumName()) }
        .getOrDefault(default)

private fun String.studyTaskTypeOrDefault(default: StudyTaskType): StudyTaskType {
    val normalized = wireEnumName()
    if (normalized == "Skim") return StudyTaskType.Reading
    return runCatching { StudyTaskType.valueOf(normalized) }.getOrDefault(default)
}

private fun String.wireEnumName() = trim()
    .lowercase()
    .split('_', '-', ' ')
    .filter { it.isNotBlank() }
    .joinToString("") { part -> part.replaceFirstChar(Char::uppercase) }

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strings() = (0 until length()).map { getString(it) }
