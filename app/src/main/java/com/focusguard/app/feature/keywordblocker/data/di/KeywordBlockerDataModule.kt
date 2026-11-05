package com.focusguard.app.feature.keywordblocker.data.di

import com.focusguard.app.feature.keywordblocker.data.repository.KeywordBlockerRepositoryImpl
import com.focusguard.app.feature.keywordblocker.domain.repository.KeywordBlockerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * =====================================================================
 * KeywordBlockerDataModule.kt - Dependency Injection (Hilt) Module
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh ek Dagger-Hilt Dependency Injection module hai jo Data Layer aur Domain Layer
 * ke beech ka connection establish karta hai. Iska main kaam [KeywordBlockerRepository]
 * interface ko uski actual implementation [KeywordBlockerRepositoryImpl] ke sath bind karna hai.
 *
 * ## 2. Architecture Me Iska Role Kya Hai? (Clean Architecture)
 * - **Abstraction & Loose Coupling:** Clean Architecture me Domain layer ko ye pata nahi hona
 *   chahiye ki data database se aa raha hai, network se, ya shared preferences se. Domain layer
 *   sirf [KeywordBlockerRepository] interface ko janti hai.
 * - **Inversion of Control (IoC):** Is module ki wajah se jab bhi koi Use Case (jaise
 *   [AddBlockedKeywordUseCase] ya [GetBlockedKeywordsUseCase]) repository maangta hai,
 *   Dagger Hilt automatically `KeywordBlockerRepositoryImpl` ka Singleton instance provide kar deta hai.
 *
 * ## 3. Technical Notes & Senior Developer Perspective:
 * - `@Binds` vs `@Provides`: Yahan `@Binds` use kiya gaya hai jo ki `@Provides` se zyada
 *   efficient aur lightweight hota hai kyunki Hilt ko koi extra factory class generate nahi
 *   karni padti—sirf ek simple cast compile time par verify hoti hai.
 * - `@Singleton` Scope: Pure app lifecycle ke dauran repository ka ek hi single instance rehta hai,
 *   jisse in-memory caching aur Room database listeners consistent rehte hain.
 * =====================================================================
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class KeywordBlockerDataModule {

    @Binds
    @Singleton
    abstract fun bindKeywordBlockerRepository(
        impl: KeywordBlockerRepositoryImpl
    ): KeywordBlockerRepository
}
