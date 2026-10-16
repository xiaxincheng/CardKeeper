package com.juxi.cardkeeper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY (dueEpochDay IS NULL), dueEpochDay ASC")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE dueEpochDay >= :fromEpochDay ORDER BY dueEpochDay ASC")
    suspend fun upcoming(fromEpochDay: Long): List<CardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CardEntity)

    @Query("SELECT * FROM cards WHERE bank = :bank AND tail = :tail AND dueEpochDay = :dueEpochDay LIMIT 1")
    suspend fun findSameBill(bank: String, tail: String, dueEpochDay: Long): CardEntity?

    @Update
    suspend fun update(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun delete(id: Long)

    /** 删除某张卡的全部账期记录（弃用清理用） */
    @Query("DELETE FROM cards WHERE bank = :bank AND tail = :tail")
    suspend fun deleteByBankTail(bank: String, tail: String)

    @Query("DELETE FROM cards WHERE dueEpochDay IS NOT NULL AND dueEpochDay < :cutoffEpochDay")
    suspend fun deleteOlderThan(cutoffEpochDay: Long)

    @Query("SELECT * FROM cards WHERE bank = :bank AND tail = :tail ORDER BY dueEpochDay DESC")
    fun observeFor(bank: String, tail: String): Flow<List<CardEntity>>

    @Query("UPDATE cards SET lastNotifiedDue = :dueEpochDay WHERE id = :id")
    suspend fun markNotified(id: Long, dueEpochDay: Long)
}

@Dao
interface MailboxDao {
    @Query("SELECT * FROM mailboxes ORDER BY id ASC")
    fun observeAll(): Flow<List<MailboxEntity>>

    @Query("SELECT * FROM mailboxes ORDER BY id ASC")
    suspend fun getAll(): List<MailboxEntity>

    @Query("SELECT * FROM mailboxes WHERE id = :id")
    suspend fun get(id: Long): MailboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mailbox: MailboxEntity): Long

    @Query("DELETE FROM mailboxes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ParseLogDao {
    @Insert
    suspend fun insert(log: ParseLogEntity)

    @Query("SELECT * FROM parse_logs ORDER BY time DESC LIMIT 300")
    fun observeRecent(): Flow<List<ParseLogEntity>>

    @Query("DELETE FROM parse_logs")
    suspend fun clear()
}

@Dao
interface IgnoredCardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(ignored: IgnoredCardEntity)

    @Query("SELECT * FROM ignored_cards ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<IgnoredCardEntity>>

    @Query("SELECT COUNT(*) FROM ignored_cards WHERE bank = :bank AND tail = :tail")
    suspend fun count(bank: String, tail: String): Int

    @Query("DELETE FROM ignored_cards WHERE bank = :bank AND tail = :tail")
    suspend fun delete(bank: String, tail: String)
}

@Dao
interface RepaymentDao {
    @Insert
    suspend fun insert(repayment: RepaymentEntity)

    @Query("SELECT * FROM repayments WHERE bank = :bank AND tail = :tail AND dueEpochDay = :dueEpochDay ORDER BY time DESC")
    fun observeFor(bank: String, tail: String, dueEpochDay: Long): Flow<List<RepaymentEntity>>

    @Query("DELETE FROM repayments WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TransactionDao {
    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE bank = :bank AND tail = :tail AND dueEpochDay = :dueEpochDay")
    suspend fun deleteForBill(bank: String, tail: String, dueEpochDay: Long)

    @Query("SELECT * FROM transactions WHERE bank = :bank AND tail = :tail AND dueEpochDay = :dueEpochDay ORDER BY txEpochDay ASC")
    fun observeFor(bank: String, tail: String, dueEpochDay: Long): Flow<List<TransactionEntity>>
}
