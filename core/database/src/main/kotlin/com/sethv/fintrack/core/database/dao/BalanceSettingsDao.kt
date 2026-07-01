package com.sethv.fintrack.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sethv.fintrack.core.database.entity.BalanceSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceSettingsDao {

    @Query("SELECT * FROM balance_settings WHERE id = 1")
    fun getSettings(): Flow<BalanceSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: BalanceSettingsEntity)
}
