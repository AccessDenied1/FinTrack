package com.sethv.fintrack.service.sms.di

import com.sethv.fintrack.service.sms.SmsProcessor
import com.sethv.fintrack.service.sms.SmsProcessorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SmsModule {

    @Binds
    @Singleton
    abstract fun bindSmsProcessor(impl: SmsProcessorImpl): SmsProcessor
}
