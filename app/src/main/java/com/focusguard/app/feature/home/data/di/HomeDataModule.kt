package com.focusguard.app.feature.home.data.di

import com.focusguard.app.feature.home.data.repository.HomeRepositoryImpl
import com.focusguard.app.feature.home.domain.repository.HomeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Hilt Dependency Injection Module (Home Feature Data Layer)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Dependency Inversion / Binding:
 *    - Binds the concrete [HomeRepositoryImpl] implementation to the domain interface [HomeRepository].
 *    - Enforces Clean Architecture boundaries: Presentation and Domain layers depend strictly
 *      on the abstract repository contract, decoupling UI logic from concrete data sources.
 *
 * 2. Scope & Lifecycle:
 *    - Installed in [SingletonComponent]: A single instance of [HomeRepository] survives the
 *      entire lifecycle of the application process.
 *    - Annotated with [@Singleton]: Ensures consistent state management across the dashboard,
 *      preventing duplicate capability manager audits or redundant database flow subscriptions.
 * =========================================================================================
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeDataModule {

    @Binds
    @Singleton
    abstract fun bindHomeRepository(
        impl: HomeRepositoryImpl
    ): HomeRepository
}
