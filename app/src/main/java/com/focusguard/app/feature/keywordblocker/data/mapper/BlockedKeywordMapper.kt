package com.focusguard.app.feature.keywordblocker.data.mapper

import com.focusguard.app.core.database.entity.BlockedKeywordEntity
import com.focusguard.app.feature.keywordblocker.domain.model.BlockedKeyword

/**
 * =====================================================================
 * BlockedKeywordMapper.kt - Data Mapping Layer (Entity <-> Domain)
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh file database entities aur business domain models ke beech conversion ka kaam karti hai.
 * Isme do pure extension functions hain:
 * - `toDomain()`: Room Database ke [BlockedKeywordEntity] ko Domain Model [BlockedKeyword] me badalta hai.
 * - `toEntity()`: Domain Model [BlockedKeyword] ko Room Database ke [BlockedKeywordEntity] me convert karta hai.
 *
 * ## 2. Architecture Me Iska Role Kya Hai? (Clean Architecture Boundary)
 * - **Separation of Concerns:** Room DB ke annotations (`@Entity`, `@PrimaryKey`, SQLite indices)
 *   sirf Data layer tak limited rehne chahiye. UI aur Domain layer ko pata nahi hona chahiye ki
 *   Room database kaise table banata hai.
 * - **Immutability & Safety:** Domain layer me ek lightweight, decoupled data class use hoti hai jo
 *   bina kisi Android ya Room framework dependency ke testable hoti hai.
 *
 * ## 3. Senior Developer Tips & Best Practices:
 * - Yeh functions pure extension functions hain jo bina kisi side-effect ke instant memory
 *   mapping karte hain (zero overhead).
 * - Agar future me database schema me koi field change hoti hai (jaise keyword categories ya tags),
 *   toh sirf is mapper ko update karna padega, pura UI aur ViewModels safe rahenge.
 * =====================================================================
 */
fun BlockedKeywordEntity.toDomain(): BlockedKeyword =
    BlockedKeyword(
        id = id,
        keyword = keyword,
        createdAt = createdAt
    )

fun BlockedKeyword.toEntity(): BlockedKeywordEntity =
    BlockedKeywordEntity(
        id = id,
        keyword = keyword,
        createdAt = createdAt
    )
