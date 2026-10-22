package com.raycoll.app.ui.decksettings

import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R
import com.raycoll.app.ui.components.LanguagePicker
import com.raycoll.app.ui.components.SettingsAction
import com.raycoll.app.ui.components.pageDisplayName

/** Nazwa strony i jej język; kolejność stron zmienia się przytrzymaniem wiersza (robi to ekran). */
@Composable
fun PageRow(
    shapes: ListItemShapes,
    pageIndex: Int,
    name: String,
    language: String,
    onEditName: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    val pageName = pageDisplayName(name, pageIndex)
    val label = stringResource(R.string.page_default_name, pageIndex + 1)
    SettingsAction(
        shapes = shapes,
        title = label,
        description = pageName.takeUnless { it == label },
        onClick = onEditName,
        trailingContent = { LanguagePicker(language, onSelect = onLanguageChange) }
    )
}
