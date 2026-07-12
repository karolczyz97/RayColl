package com.raycoll.app.core

import java.util.UUID

object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}
