package com.focusguard.app.feature.mindful.data.di

import com.focusguard.app.feature.mindful.data.repository.MindfulAppsRepositoryImpl
import com.focusguard.app.feature.mindful.domain.repository.MindfulAppsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MindfulDataModule {

    @Binds
    @Singleton
    abstract fun bindMindfulAppsRepository(
        impl: MindfulAppsRepositoryImpl
    ): MindfulAppsRepository
}
