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

    @Test fun migrateFromVersionOneToThreeCreatesCurrentSchema() {
        helper.createDatabase("study-projects-migration-1-3", 1).close()

        val database = helper.runMigrationsAndValidate(
            "study-projects-migration-1-3",
            3,
            true,
            DropLegacyStudyProjectsMigration,
            AddDailyAvailableMinutesMigration,
        )

        assertTrue(database.hasColumn("active_study_project", "dailyAvailableMinutesJson"))
        database.close()
    }

    @Test fun migrateFromVersionTwoToThreeAddsDailyAvailabilityColumn() {
        helper.createDatabase("study-projects-migration-2-3", 2).close()

        val database = helper.runMigrationsAndValidate(
            "study-projects-migration-2-3",
            3,
            true,
            AddDailyAvailableMinutesMigration,
        )

        assertTrue(database.hasColumn("active_study_project", "dailyAvailableMinutesJson"))
        database.close()
    }
}

private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
            .any { it == column }
    }
