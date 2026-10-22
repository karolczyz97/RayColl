package com.raycoll.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R

/** Nazwa strony do pokazania: wpisana albo domyślna „Strona N”. */
@Composable
fun pageDisplayName(name: String?, index: Int): String =
    name.orEmpty().ifBlank { stringResource(R.string.page_default_name, index + 1) }
