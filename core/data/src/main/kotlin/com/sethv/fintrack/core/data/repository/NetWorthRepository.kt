package com.sethv.fintrack.core.data.repository

import com.sethv.fintrack.core.model.NetWorthState
import kotlinx.coroutines.flow.Flow

interface NetWorthRepository {
    fun getNetWorthState(): Flow<NetWorthState>
    suspend fun setInitialBalance(amount: Double)
    fun hasInitialBalance(): Flow<Boolean>
}
