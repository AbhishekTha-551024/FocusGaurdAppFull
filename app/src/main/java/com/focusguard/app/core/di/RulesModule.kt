package com.focusguard.app.core.di

import com.focusguard.app.core.engine.BlockerRule
import com.focusguard.app.core.engine.rules.AntiClonerRule
import com.focusguard.app.core.engine.rules.BlockedAppRule
import com.focusguard.app.core.engine.rules.MindfulAppRule
import com.focusguard.app.core.engine.rules.SelfPackageExclusionRule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class RulesModule {

    @Binds
    @IntoSet
    abstract fun bindSelfPackageExclusionRule(impl: SelfPackageExclusionRule): BlockerRule

    @Binds
    @IntoSet
    abstract fun bindAntiClonerRule(impl: AntiClonerRule): BlockerRule

    @Binds
    @IntoSet
    abstract fun bindBlockedAppRule(impl: BlockedAppRule): BlockerRule

    @Binds
    @IntoSet
    abstract fun bindMindfulAppRule(impl: MindfulAppRule): BlockerRule
}
