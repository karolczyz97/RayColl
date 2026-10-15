package com.raycoll.app.data.account

import kotlinx.coroutines.flow.StateFlow

interface CurrentAccount {
    val uid: StateFlow<String?>
}
