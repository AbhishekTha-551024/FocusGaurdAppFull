package com.focusguard.app.feature.appblocker.data.di

import com.focusguard.app.feature.appblocker.data.repository.AppBlockerRepositoryImpl
import com.focusguard.app.feature.appblocker.domain.repository.AppBlockerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBlockerDataModule {

    @Binds
    @Singleton
    abstract fun bindAppBlockerRepository(
        impl: AppBlockerRepositoryImpl
    ): AppBlockerRepository
}
