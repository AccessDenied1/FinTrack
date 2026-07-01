package com.sethv.fintrack.core.data.repository

import com.sethv.fintrack.core.data.mapper.toDomain
import com.sethv.fintrack.core.database.dao.BalanceSettingsDao
import com.sethv.fintrack.core.database.dao.TransactionDao
import com.sethv.fintrack.core.database.entity.BalanceSettingsEntity
import com.sethv.fintrack.core.model.NetWorthState
import com.sethv.fintrack.core.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NetWorthRepositoryImpl @Inject constructor(
    private val balanceSettingsDao: BalanceSettingsDao,
    private val transactionDao: TransactionDao,
) : NetWorthRepository {

    override fun getNetWorthState(): Flow<NetWorthState> =
        combine(
            balanceSettingsDao.getSettings(),
            transactionDao.getAll(),
        ) { settings, transactions ->
            val initialBalance = settings?.initialBalance ?: 0.0
            val domainTransactions = transactions.map { it.toDomain() }
            val totalCredits = domainTransactions
                .filter { it.type == TransactionType.CREDIT }
                .sumOf { it.amount }
            val totalDebits = domainTransactions
                .filter { it.type == TransactionType.DEBIT }
                .sumOf { it.amount }
            val currentBalance = initialBalance + totalCredits - totalDebits

            NetWorthState(
                initialBalance = initialBalance,
                currentBalance = currentBalance,
                totalCredits = totalCredits,
                totalDebits = totalDebits,
            )
        }

    override suspend fun setInitialBalance(amount: Double) {
        balanceSettingsDao.upsertSettings(
            BalanceSettingsEntity(
                id = 1,
                initialBalance = amount,
                setAt = System.currentTimeMillis(),
            )
        )
    }

    override fun hasInitialBalance(): Flow<Boolean> =
        balanceSettingsDao.getSettings().map { it != null }
}
