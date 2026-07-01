package com.sethv.fintrack.service.parser.di

import com.sethv.fintrack.service.parser.CompositeSmsParser
import com.sethv.fintrack.service.parser.SmsParser
import com.sethv.fintrack.service.parser.impl.AxisBankParser
import com.sethv.fintrack.service.parser.impl.GenericUpiParser
import com.sethv.fintrack.service.parser.impl.HdfcBankParser
import com.sethv.fintrack.service.parser.impl.IciciBankParser
import com.sethv.fintrack.service.parser.impl.SbiBankParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ParserModule {

    @Provides
    @Singleton
    fun provideSmsParser(
        hdfcBankParser: HdfcBankParser,
        sbiBankParser: SbiBankParser,
        iciciBankParser: IciciBankParser,
        axisBankParser: AxisBankParser,
        genericUpiParser: GenericUpiParser,
    ): SmsParser = CompositeSmsParser(
        linkedSetOf(
            hdfcBankParser,
            sbiBankParser,
            iciciBankParser,
            axisBankParser,
            genericUpiParser,
        ),
    )
}
