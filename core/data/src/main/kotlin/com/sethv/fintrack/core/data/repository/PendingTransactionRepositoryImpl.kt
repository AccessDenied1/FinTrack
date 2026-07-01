package com.sethv.fintrack.core.data.repository

import com.sethv.fintrack.core.data.mapper.toDomain
import com.sethv.fintrack.core.data.mapper.toEntity
import com.sethv.fintrack.core.database.dao.PendingTransactionDao
import com.sethv.fintrack.core.model.PendingStatus
import com.sethv.fintrack.core.model.PendingTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PendingTransactionRepositoryImpl @Inject constructor(
    private val pendingTransactionDao: PendingTransactionDao,
) : PendingTransactionRepository {

    override suspend fun insertPending(pending: PendingTransaction): Long =
        pendingTransactionDao.insert(pending.toEntity())

    override fun getAllPending(): Flow<List<PendingTransaction>> =
        pendingTransactionDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getPendingById(id: Long): PendingTransaction? =
        pendingTransactionDao.getById(id)?.toDomain()

    override suspend fun acceptPending(id: Long) {
        pendingTransactionDao.updateStatus(id, PendingStatus.ACCEPTED.name)
    }

    override suspend fun rejectPending(id: Long) {
        pendingTransactionDao.updateStatus(id, PendingStatus.REJECTED.name)
    }

    override suspend fun deletePending(id: Long) {
        pendingTransactionDao.deletePending(id)
    }
}
