package com.hci.ren.feature.studymap

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StudyProjectDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudyProjectDatabase::class.java,
    )

    @Test fun migrateFromVersionOneToFourCreatesCurrentSchema() {
        helper.createDatabase("study-projects-migration-1-4", 1).close()

        val database = helper.runMigrationsAndValidate(
            "study-projects-migration-1-4",
            4,
            true,
            DropLegacyStudyProjectsMigration,
            AddDailyAvailableMinutesMigration,
            AddTaskProgressMigration,
        )

        assertTrue(database.hasColumn("active_study_project", "dailyAvailableMinutesJson"))
        assertTrue(database.hasColumn("active_study_project", "taskProgressJson"))
        database.close()
    }

    @Test fun migrateFromVersionTwoToFourAddsActiveStateColumns() {
        helper.createDatabase("study-projects-migration-2-4", 2).close()

        val database = helper.runMigrationsAndValidate(
            "study-projects-migration-2-4",
            4,
            true,
            AddDailyAvailableMinutesMigration,
            AddTaskProgressMigration,
        )

        assertTrue(database.hasColumn("active_study_project", "dailyAvailableMinutesJson"))
        assertTrue(database.hasColumn("active_study_project", "taskProgressJson"))
        database.close()
    }

    @Test fun migrateFromVersionThreeToFourAddsTaskProgressColumn() {
        helper.createDatabase("study-projects-migration-3-4", 3).close()

        val database = helper.runMigrationsAndValidate(
            "study-projects-migration-3-4",
            4,
            true,
            AddTaskProgressMigration,
        )

        assertTrue(database.hasColumn("active_study_project", "taskProgressJson"))
        database.close()
    }
}

private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
            .any { it == column }
    }
