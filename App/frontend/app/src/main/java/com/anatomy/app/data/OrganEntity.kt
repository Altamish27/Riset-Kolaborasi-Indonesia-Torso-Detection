package com.anatomy.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * OrganEntity — Room entity for the 'organs' table.
 *
 * Stores organ name, short description (for quick prompts),
 * and long description (for detailed TTS explanations).
 */
@Entity(tableName = "organs")
data class OrganEntity(
    @PrimaryKey
    val name: String,
    val short_description: String,
    val long_description: String
)
