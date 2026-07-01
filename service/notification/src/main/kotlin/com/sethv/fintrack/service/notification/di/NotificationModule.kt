package com.sethv.fintrack.service.notification.di

import com.sethv.fintrack.service.notification.TransactionNotifier
import com.sethv.fintrack.service.notification.TransactionNotifierImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    @Singleton
    abstract fun bindTransactionNotifier(
        impl: TransactionNotifierImpl,
    ): TransactionNotifier
}
