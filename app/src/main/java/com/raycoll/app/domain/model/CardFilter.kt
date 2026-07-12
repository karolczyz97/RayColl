package com.raycoll.app.domain.model

import kotlinx.serialization.Serializable

enum class CardFilter { all, new, review, new_review }

enum class CardOrder { sequential, random, hardest }
