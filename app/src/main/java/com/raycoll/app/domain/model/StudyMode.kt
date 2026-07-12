package com.raycoll.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class StudyMode(
    val id: String,
    val name: String,
    val steps: List<ModeStep> = emptyList(),
    val isBuiltIn: Boolean = false,
    val builtInSourceId: String? = null,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)
