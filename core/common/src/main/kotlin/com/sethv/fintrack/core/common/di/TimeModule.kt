package com.sethv.fintrack.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    /** Injectable clock so time-dependent logic (month windows) is unit-testable. */
    @Provides
    @Singleton
    fun providesClock(): Clock = Clock.systemDefaultZone()
}
