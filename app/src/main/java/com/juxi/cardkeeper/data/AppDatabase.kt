package com.juxi.cardkeeper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CardEntity::class, MailboxEntity::class, ParseLogEntity::class,
        IgnoredCardEntity::class, RepaymentEntity::class, TransactionEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun mailboxDao(): MailboxDao
    abstract fun parseLogDao(): ParseLogDao
    abstract fun ignoredCardDao(): IgnoredCardDao
    abstract fun repaymentDao(): RepaymentDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // v1 → v2：新增 ignored_cards（弃用卡忽略名单）表
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ignored_cards` (" +
                        "`bank` TEXT NOT NULL, `tail` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, PRIMARY KEY (`bank`, `tail`))"
                )
            }
        }

        // v2 → v3：cards 表新增 holder（持卡人）列
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `holder` TEXT NOT NULL DEFAULT ''")
            }
        }

        // v3 → v4：还款记录表 + cards 表已还金额/信用额度
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `paidAmount` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `creditLimit` REAL")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `repayments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`time` INTEGER NOT NULL, `amount` REAL NOT NULL, `note` TEXT NOT NULL, " +
                        "`bank` TEXT NOT NULL, `tail` TEXT NOT NULL, `dueEpochDay` INTEGER NOT NULL)"
                )
            }
        }

        // v4 → v5：交易明细表（账单里的每笔消费）
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transactions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bank` TEXT NOT NULL, `tail` TEXT NOT NULL, `dueEpochDay` INTEGER NOT NULL, " +
                        "`txEpochDay` INTEGER, `memo` TEXT NOT NULL, `amount` REAL NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_bank_tail_dueEpochDay` " +
                        "ON `transactions` (`bank`, `tail`, `dueEpochDay`)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cardkeeper.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
