package com.raycoll.app.ui.modedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.raycoll.app.R
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.withCondition
import com.raycoll.app.ui.components.ChoiceChip
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.theme.Spacing

/** Jeden krok trybu: tytuł, przestawianie, warunek i parametry. */
@Composable
fun StepCard(
    number: Int,
    step: ModeStep,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (ModeStep) -> Unit,
    modifier: Modifier = Modifier,
    onFieldValidityChange: (field: Int, valid: Boolean) -> Unit = { _, _ -> },
    onAddBelow: (() -> Unit)? = null
) {
    // tytuł kroku w opisach przycisków, żeby czytnik ekranu odróżniał kolejne karty
    val title = stringResource(R.string.mode_editor_step_title, number, stepTitle(step))
    RayCollCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(
                            R.string.a11y_item_action,
                            stringResource(R.string.mode_editor_move_up),
                            title
                        )
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(
                            R.string.a11y_item_action,
                            stringResource(R.string.mode_editor_move_down),
                            title
                        )
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(
                            R.string.a11y_item_action,
                            stringResource(R.string.btn_delete),
                            title
                        ),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (step !is CompoundStep) ConditionSelector(step, onUpdate)
            CompositionLocalProvider(LocalFieldValidity provides onFieldValidityChange) {
                StepParamFields(step, onUpdate)
            }
            if (onAddBelow != null) {
                TextButton(onClick = onAddBelow, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.mode_editor_add_below))
                }
            }
        }
    }
}

@Composable
private fun ConditionSelector(step: ModeStep, onUpdate: (ModeStep) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        listOf(
            null to R.string.step_condition_always,
            StepCondition.CORRECT to R.string.step_condition_correct,
            StepCondition.WRONG to R.string.step_condition_wrong
        ).forEach { (condition, label) ->
            ChoiceChip(
                selected = step.condition == condition,
                onClick = { onUpdate(step.withCondition(condition)) },
                label = { Text(stringResource(label)) }
            )
        }
    }
}
