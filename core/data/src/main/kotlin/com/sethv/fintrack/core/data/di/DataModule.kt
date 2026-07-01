package com.sethv.fintrack.core.data.di

import com.sethv.fintrack.core.data.repository.NetWorthRepository
import com.sethv.fintrack.core.data.repository.NetWorthRepositoryImpl
import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.data.repository.PendingTransactionRepositoryImpl
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.data.repository.TransactionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        impl: TransactionRepositoryImpl,
    ): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindPendingTransactionRepository(
        impl: PendingTransactionRepositoryImpl,
    ): PendingTransactionRepository

    @Binds
    @Singleton
    abstract fun bindNetWorthRepository(
        impl: NetWorthRepositoryImpl,
    ): NetWorthRepository
}
