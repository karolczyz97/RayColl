package com.raycoll.app.core

import java.util.UUID

object Ids {
    fun uid(): String = UUID.randomUUID().toString()
}
