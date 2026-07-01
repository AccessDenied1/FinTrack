package com.sethv.fintrack.service.categorizer.di

import com.sethv.fintrack.service.categorizer.KeywordBasedCategorizer
import com.sethv.fintrack.service.categorizer.TransactionCategorizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CategorizerModule {

    @Binds
    @Singleton
    abstract fun bindTransactionCategorizer(
        impl: KeywordBasedCategorizer,
    ): TransactionCategorizer
}
