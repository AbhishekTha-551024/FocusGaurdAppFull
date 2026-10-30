package com.focusguard.app.core.di

import com.focusguard.app.core.engine.BlockDecisionEngine
import com.focusguard.app.core.engine.BlockerRule
import com.focusguard.app.core.engine.DefaultBlockDecisionEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Multibinds
    abstract fun bindBlockerRules(): Set<BlockerRule>

    @Binds
    @Singleton
    abstract fun bindBlockDecisionEngine(impl: DefaultBlockDecisionEngine): BlockDecisionEngine
}
