package com.focusguard.app.di

import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.data.commitment.FirestoreCommitmentLockRepository
import com.focusguard.app.data.identity.AndroidIdDeviceIdentityRepository
import com.focusguard.app.domain.repository.CommitmentLockRepository
import com.focusguard.app.domain.repository.DeviceIdentityRepository
import com.focusguard.app.domain.repository.LocalCommitmentStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings for the reinstall-restore flow: the reinstall-stable device identity and the durable,
 * device-keyed commitment lock record.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommitmentDataModule {

    @Binds
    @Singleton
    abstract fun bindDeviceIdentityRepository(
        impl: AndroidIdDeviceIdentityRepository
    ): DeviceIdentityRepository

    @Binds
    @Singleton
    abstract fun bindCommitmentLockRepository(
        impl: FirestoreCommitmentLockRepository
    ): CommitmentLockRepository

    /**
     * The local half of the restore comparison. [com.focusguard.app.core.datastore.FocusGuardPreferences]
     * already exposes exactly these members, so this is a binding, not a wrapper.
     */
    @Binds
    @Singleton
    abstract fun bindLocalCommitmentStore(
        impl: FocusGuardPreferences
    ): LocalCommitmentStore
}
