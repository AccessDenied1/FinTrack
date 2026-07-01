package com.sethv.fintrack.core.data.repository

import com.sethv.fintrack.core.data.mapper.toDomain
import com.sethv.fintrack.core.data.mapper.toEntity
import com.sethv.fintrack.core.database.dao.TransactionDao
import com.sethv.fintrack.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
) : TransactionRepository {

    override suspend fun insertTransaction(transaction: Transaction): Long =
        transactionDao.insert(transaction.toEntity())

    override fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTransactionById(id: Long): Transaction? =
        transactionDao.getById(id)?.toDomain()

    override suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteById(id)
    }

    override fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>> =
        transactionDao.getByDateRange(startTime, endTime).map { entities ->
            entities.map { it.toDomain() }
        }
}
