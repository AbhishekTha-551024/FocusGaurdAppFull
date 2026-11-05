package com.focusguard.app.di

import com.focusguard.app.receiver.NewAppInstallReceiver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReceiverModule {

    @Provides
    @Singleton
    fun provideNewAppInstallReceiver(): NewAppInstallReceiver = NewAppInstallReceiver()
}
