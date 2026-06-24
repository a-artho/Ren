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
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.feature.plangeneration.StudyTopic
import com.hci.ren.feature.plangeneration.TaskDisposition
import com.hci.ren.feature.plangeneration.TaskPriority
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

@Entity(tableName = "study_projects")
data class StudyProjectEntity(
    @PrimaryKey val id: String,
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
    @Query("SELECT * FROM study_projects ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<StudyProjectEntity>>

    @Query("SELECT * FROM study_projects WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<StudyProjectEntity?>

    @Query("SELECT * FROM study_projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StudyProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: StudyProjectEntity)

    @Query("DELETE FROM study_projects WHERE id = :id")
    suspend fun deleteById(id: String): Int
}

@Database(entities = [StudyProjectEntity::class], version = 1, exportSchema = true)
abstract class StudyProjectDatabase : RoomDatabase() {
    abstract fun studyProjectDao(): StudyProjectDao

    companion object {
        @Volatile private var instance: StudyProjectDatabase? = null

        fun getInstance(context: Context): StudyProjectDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StudyProjectDatabase::class.java,
                "ren-study-projects.db",
            ).build().also { instance = it }
        }
    }
}

class StudyProjectRepository(private val dao: StudyProjectDao) {
    fun observeAll(): Flow<List<StudyProject>> = dao.observeAll().map { entities ->
        entities.map(StudyProjectJsonCodec::decode)
    }

    fun observeById(id: String): Flow<StudyProject?> = dao.observeById(id).map { entity ->
        entity?.let(StudyProjectJsonCodec::decode)
    }

    suspend fun getById(id: String): StudyProject? = dao.getById(id)?.let(StudyProjectJsonCodec::decode)

    suspend fun upsert(project: StudyProject) = dao.upsert(StudyProjectJsonCodec.encode(project))

    suspend fun saveGeneratedPlan(plan: GeneratedStudyPlan, preferences: PlanSetupSubmission) {
        val generated = newStudyProject(plan, preferences)
        val existing = dao.getById(generated.id)?.let(StudyProjectJsonCodec::decode)
        dao.upsert(StudyProjectJsonCodec.encode(
            generated.copy(createdAtMillis = existing?.createdAtMillis ?: generated.createdAtMillis),
        ))
    }

    suspend fun delete(id: String) {
        check(dao.deleteById(id) > 0) { "Study project no longer exists" }
    }

    companion object {
        fun create(context: Context) = StudyProjectRepository(
            StudyProjectDatabase.getInstance(context).studyProjectDao(),
        )
    }
}

internal object StudyProjectJsonCodec {
    fun encode(project: StudyProject) = StudyProjectEntity(
        id = project.id,
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
            id = entity.id,
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
    val absoluteDeadline = deadlineDate(preferences, Calendar.getInstance())?.timeInMillis
    val persistedPreferences = if (absoluteDeadline == null) {
        preferences.copy(documentUri = "", deadline = StudyDeadline.NoFixedDeadline, deadlineDate = null)
    } else {
        preferences.copy(
            documentUri = "",
            deadline = StudyDeadline.ChooseDate,
            deadlineDate = Calendar.getInstance().apply { timeInMillis = absoluteDeadline }.toStudyDate(),
        )
    }
    val title = plan.projectName.safeStudyProjectTitle()
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

internal fun String?.safeStudyProjectTitle(): String = this?.trim().orEmpty().ifBlank { "Untitled Study Map" }

private fun GeneratedStudyPlan.toJson() = JSONObject()
    .put("id", id)
    .put("projectName", projectName)
    .put("totalEstimatedMinutes", totalEstimatedMinutes)
    .put("topics", JSONArray(topics.map { topic ->
        JSONObject().put("id", topic.id).put("title", topic.title).put("order", topic.order)
    }))
    .put("blocks", JSONArray(blocks.map(GeneratedStudyBlock::toJson)))

private fun GeneratedStudyBlock.toJson() = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("order", order)
    .put("durationMinutes", durationMinutes)
    .put("instructions", instructions)
    .put("topicIds", JSONArray(topicIds))
    .put("minimumUsefulMinutes", minimumUsefulMinutes)
    .put("priority", priority.name)
    .put("taskType", taskType.name)
    .put("priorityReason", priorityReason)
    .put("isSkippable", isSkippable)
    .put("disposition", disposition.name)
    .put("status", status.name)
    .put("scheduledDate", scheduledDate)
    .put("dependencies", JSONArray(dependencies))
    .put("isOptional", isOptional)
    .put("isExcluded", isExcluded)

private fun JSONObject.toPlan(): GeneratedStudyPlan = GeneratedStudyPlan(
    id = getString("id"),
    topics = getJSONArray("topics").objects().map {
        StudyTopic(it.getString("id"), it.getString("title"), it.getInt("order"))
    }.sortedBy { it.order },
    blocks = getJSONArray("blocks").objects().map(JSONObject::toBlock).sortedBy { it.order },
    totalEstimatedMinutes = optInt("totalEstimatedMinutes"),
    projectName = optString("projectName").safeStudyProjectTitle(),
)

private fun JSONObject.toBlock() = GeneratedStudyBlock(
    id = getString("id"),
    title = getString("title"),
    order = getInt("order"),
    durationMinutes = optInt("durationMinutes", 1).coerceAtLeast(1),
    instructions = optString("instructions"),
    topicIds = getJSONArray("topicIds").strings(),
    minimumUsefulMinutes = optInt("minimumUsefulMinutes", 10).coerceAtLeast(1),
    priority = optString("priority").enumOr(TaskPriority.Medium),
    taskType = optString("taskType").enumOr(StudyTaskType.Review),
    priorityReason = optString("priorityReason", "Supports the study goal"),
    isSkippable = optBoolean("isSkippable", true),
    disposition = optString("disposition").enumOr(TaskDisposition.MustComplete),
    status = optString("status").enumOr(StudyTaskStatus.NotStarted),
    scheduledDate = optString("scheduledDate").takeUnless { it.isBlank() || it == "null" },
    dependencies = optJSONArray("dependencies")?.strings().orEmpty(),
    isOptional = optBoolean("isOptional"),
    isExcluded = optBoolean("isExcluded"),
)

private fun PlanSetupSubmission.toPersistedJson() = JSONObject()
    .put("goal", goal.name)
    .put("deadline", deadline.name)
    .put("deadlineDate", deadlineDate)
    .put("dailyStudyMinutes", dailyStudyMinutes)
    .put("studyDays", JSONArray(studyDays.map { it.name }))

private fun JSONObject.toSubmission() = PlanSetupSubmission(
    documentUri = "",
    goal = getString("goal").enumOr(StudyGoal.OngoingStudy),
    deadline = getString("deadline").enumOr(StudyDeadline.NoFixedDeadline),
    deadlineDate = optString("deadlineDate").takeUnless { it.isBlank() || it == "null" },
    dailyStudyMinutes = optInt("dailyStudyMinutes", 30).coerceIn(1, 1_440),
    studyDays = getJSONArray("studyDays").strings().mapNotNull { runCatching { StudyDay.valueOf(it) }.getOrNull() }.toSet()
        .ifEmpty { StudyDay.entries.toSet() },
)

private inline fun <reified T : Enum<T>> String.enumOr(default: T): T =
    runCatching { enumValueOf<T>(this) }.getOrDefault(default)

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strings() = (0 until length()).map { getString(it) }
