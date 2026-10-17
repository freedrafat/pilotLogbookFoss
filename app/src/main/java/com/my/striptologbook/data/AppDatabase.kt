package com.my.striptologbook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Leg::class, OpeningBalance::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun legDao(): LegDao
    abstract fun openingBalanceDao(): OpeningBalanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE legs ADD COLUMN numberInstrumentApproaches INTEGER"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `opening_balances` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `startDate` INTEGER,
                        `endDate` INTEGER,
                        `acType` TEXT,
                        `position` TEXT,
                        `category` TEXT,
                        `comment` TEXT,
                        `blockTimeTotal` INTEGER,
                        `blockTimeNight` INTEGER,
                        `blockTimeDay` INTEGER,
                        `flightTimeTotal` INTEGER,
                        `flightTimeNight` INTEGER,
                        `flightTimeDay` INTEGER,
                        `ifrTotal` INTEGER,
                        `ifrNight` INTEGER,
                        `ifrDay` INTEGER,
                        `simulatorTime` INTEGER,
                        `numberToDay` INTEGER,
                        `numberToNight` INTEGER,
                        `numberToTotal` INTEGER,
                        `numberLdgDay` INTEGER,
                        `numberLdgNight` INTEGER,
                        `numberLdgTotal` INTEGER,
                        `numberInstrumentApproaches` INTEGER,
                        `singlePilotSeTime` INTEGER,
                        `singlePilotMeTime` INTEGER,
                        `multiPilotTime` INTEGER,
                        `pilotFunctionPicTime` INTEGER,
                        `pilotFunctionSicTime` INTEGER,
                        `pilotFunctionDualTime` INTEGER,
                        `pilotFunctionInstructorTime` INTEGER
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "logbook_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}