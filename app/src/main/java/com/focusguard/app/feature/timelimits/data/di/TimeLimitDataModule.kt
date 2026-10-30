package com.focusguard.app.feature.timelimits.data.di

import com.focusguard.app.feature.timelimits.data.repository.TimeLimitRepositoryImpl
import com.focusguard.app.feature.timelimits.domain.repository.TimeLimitRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * =====================================================================
 * TimeLimitDataModule.kt - Dependency Injection (Hilt) Module
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Hilt Dagger dependency injection module hai jo Time Limits feature ki
 * abstraction boundary ko wire up karta hai. Yeh [TimeLimitRepository] interface ko
 * uski concrete implementation [TimeLimitRepositoryImpl] ke sath bind karta hai.
 *
 * ## 2. Architecture Me Iska Role Kya Hai? (Clean Architecture Boundary)
 * - **Decoupling:** Domain layer ke Use Cases (jaise [SetTimeLimitUseCase], [GetTimeLimitsUseCase])
 *   sirf interface [TimeLimitRepository] ko jante hain. Unhe yeh nahi pata hota ki data
 *   Room DB se aa raha hai, Android UsageStatsManager se, ya in-memory evaluator se.
 * - **Inversion of Control (IoC):** Hilt compile-time par graph verify karta hai aur single instance inject karta hai.
 *
 * ## 3. Senior Dev Notes:
 * - `@Binds` use kiya gaya hai kyunki yeh `@Provides` se fast aur memory-efficient hai.
 * - `@Singleton` ensures pure app lifecycle me repository aur uske under ke cached states ek hi rahen.
 * =====================================================================
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimeLimitDataModule {

    @Binds
    @Singleton
    abstract fun bindTimeLimitRepository(
        impl: TimeLimitRepositoryImpl
    ): TimeLimitRepository
}
