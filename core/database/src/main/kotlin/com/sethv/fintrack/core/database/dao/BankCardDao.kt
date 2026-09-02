package com.sethv.fintrack.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sethv.fintrack.core.database.entity.BankCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankCardDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BankCardEntity): Long

    @Update
    suspend fun update(entity: BankCardEntity)

    @Query("SELECT * FROM credit_cards ORDER BY createdAt ASC")
    fun getAll(): Flow<List<BankCardEntity>>

    @Query("SELECT * FROM credit_cards WHERE id = :id")
    suspend fun getById(id: Long): BankCardEntity?

    @Query("SELECT * FROM credit_cards WHERE bankName = :bankName AND lastFour = :lastFour LIMIT 1")
    suspend fun findByBankAndLastFour(bankName: String, lastFour: String): BankCardEntity?

    @Query("UPDATE credit_cards SET label = :label WHERE id = :id")
    suspend fun rename(id: Long, label: String)
}
