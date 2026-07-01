package com.sethv.fintrack.service.sms.di

import android.content.ContentResolver
import android.content.Context
import com.sethv.fintrack.service.sms.SmsProcessor
import com.sethv.fintrack.service.sms.SmsProcessorImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SmsModule {

    @Binds
    @Singleton
    abstract fun bindSmsProcessor(impl: SmsProcessorImpl): SmsProcessor

    companion object {
        @Provides
        @Singleton
        fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
            context.contentResolver
    }
}
