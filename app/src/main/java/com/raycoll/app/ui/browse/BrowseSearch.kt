package com.raycoll.app.ui.browse

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R

/**
 * Pole wyszukiwania fiszek, które filtruje listę pod sobą. To pole z Material 3 SearchBar
 * (ten sam wygląd), ale bez rozwijania wyników na osobny ekran — sam SearchBar zawsze je rozwija.
 */
@Composable
internal fun BrowseSearchField(query: String, onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState(query)
    val keyboard = LocalSoftwareKeyboardController.current
    // pole jest źródłem tekstu; filtr w ViewModelu liczy się w tle i nie blokuje pisania
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect(onSearch)
    }
    // „Wyczyść filtry” z pustej listy czyści też pole
    LaunchedEffect(query) {
        if (query.isEmpty() && textFieldState.text.isNotEmpty()) textFieldState.clearText()
    }
    Surface(
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
        modifier = modifier
    ) {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { keyboard?.hide() },
            placeholder = { Text(stringResource(R.string.browse_search_placeholder)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = if (textFieldState.text.isNotEmpty()) {
                {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { textFieldState.clearText() }) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.browse_clear_search))
                    }
                }
            } else {
                null
            }
        )
    }
}
