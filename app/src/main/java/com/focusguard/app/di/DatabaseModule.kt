package com.focusguard.app.di

import android.content.Context
import androidx.room.Room
import com.focusguard.app.core.database.FocusGuardDatabase
import com.focusguard.app.core.database.dao.*
import com.focusguard.app.core.database.entity.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FocusGuardDatabase =
        Room.databaseBuilder(
            context,
            FocusGuardDatabase::class.java,
            "focusguard.db"
        )
            .addMigrations(*com.focusguard.app.core.database.DatabaseMigrations.ALL_MIGRATIONS)
            .build()

    @Provides
    @Singleton
    fun provideSecurityEventDao(db: FocusGuardDatabase): SecurityEventDao =
        db.securityEventDao()

    @Provides
    @Singleton
    fun provideBlockedAppDao(db: FocusGuardDatabase): BlockedAppDao =
        db.blockedAppDao()

    @Provides
    @Singleton
    fun provideBlockedKeywordDao(db: FocusGuardDatabase): BlockedKeywordDao =
        db.blockedKeywordDao()

    @Provides
    @Singleton
    fun provideAppTimeLimitDao(db: FocusGuardDatabase): AppTimeLimitDao =
        db.appTimeLimitDao()

    @Provides
    @Singleton
    fun provideScheduleDao(db: FocusGuardDatabase): ScheduleDao =
        db.scheduleDao()

    @Provides
    @Singleton
    fun providePendingChangeDao(db: FocusGuardDatabase): PendingChangeDao =
        db.pendingChangeDao()

    @Provides
    @Singleton
    fun providePartnershipDao(db: FocusGuardDatabase): PartnershipDao =
        db.partnershipDao()

    @Provides
    @Singleton
    fun provideMindfulDao(db: FocusGuardDatabase): MindfulDao =
        db.mindfulDao()
}
