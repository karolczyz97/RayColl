package com.raycoll.app.ui.modedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.raycoll.app.R
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.withCondition
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.components.SingleChoiceButtonGroup
import com.raycoll.app.ui.components.SwipeToDeleteItem
import com.raycoll.app.ui.theme.Spacing

/**
 * Jeden krok trybu: tytuł, warunek i parametry. Usuwa się go przesunięciem w lewo (jak fiszkę), a przestawia
 * przytrzymaniem — to drugie dodaje ekran przez [modifier], a [reorderActions] to jego odpowiednik dla czytnika ekranu.
 */
@Composable
fun StepCard(
    number: Int,
    step: ModeStep,
    onDelete: () -> Unit,
    onUpdate: (ModeStep) -> Unit,
    modifier: Modifier = Modifier,
    onFieldValidityChange: (field: Int, valid: Boolean) -> Unit = { _, _ -> },
    onAddBelow: (() -> Unit)? = null,
    reorderActions: List<CustomAccessibilityAction> = emptyList()
) {
    // tytuł kroku w opisie usuwania, żeby czytnik ekranu odróżniał kolejne karty
    val title = stringResource(R.string.mode_editor_step_title, number, stepTitle(step))
    SwipeToDeleteItem(
        onDelete = onDelete,
        deleteLabel = stringResource(R.string.a11y_item_action, stringResource(R.string.btn_delete), title),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
        extraActions = reorderActions
    ) {
        RayCollCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )
                if (step !is CompoundStep) ConditionSelector(step, onUpdate)
                CompositionLocalProvider(LocalFieldValidity provides onFieldValidityChange) {
                    StepParamFields(step, onUpdate)
                }
                if (onAddBelow != null) {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onAddBelow,
                        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.mode_editor_add_below))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionSelector(step: ModeStep, onUpdate: (ModeStep) -> Unit) {
    SingleChoiceButtonGroup(
        options = listOf(
            null to stringResource(R.string.step_condition_always),
            StepCondition.CORRECT to stringResource(R.string.step_condition_correct),
            StepCondition.WRONG to stringResource(R.string.step_condition_wrong)
        ),
        selected = step.condition,
        onSelect = { onUpdate(step.withCondition(it)) }
    )
}
