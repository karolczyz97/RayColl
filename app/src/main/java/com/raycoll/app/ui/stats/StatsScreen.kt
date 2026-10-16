package com.raycoll.app.ui.stats

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.selectors.CardStats
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.ui.components.CardStatsBar
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.SectionCard
import com.raycoll.app.ui.components.srsCategoryIcon
import com.raycoll.app.ui.theme.LocalSrsColors
import com.raycoll.app.ui.theme.Spacing

@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.stats_title), onBack = onBack) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (val state = uiState) {
            StatsUiState.Loading -> LoadingState(contentModifier)

            is StatsUiState.Ready -> LazyColumn(
                modifier = contentModifier,
                contentPadding = Spacing.screenContent(),
                verticalArrangement = Arrangement.spacedBy(Spacing.item)
            ) {
                item(key = "streak") { StreakCard(state.streak) }
                item(key = "heatmap") {
                    SectionCard(
                        title = pluralStringResource(R.plurals.stats_heatmap_title, HEATMAP_WEEKS, HEATMAP_WEEKS),
                        spacing = Spacing.s
                    ) {
                        ActivityHeatmap(state.heatmap, state.streak, state.today)
                    }
                }
                item(key = "distribution") {
                    SectionCard(
                        title = stringResource(R.string.stats_cards_total, state.allCards.total),
                        spacing = Spacing.s
                    ) {
                        // ten sam układ co karty talii poniżej: pasek, a pod nim kafelki
                        CardStatsBar(state.allCards)
                        SrsDistribution(state.allCards)
                    }
                }
                items(state.decks, key = { it.id }) { deck ->
                    SectionCard(title = deck.name, spacing = Spacing.s) {
                        CardStatsBar(deck.stats)
                        SrsDistribution(deck.stats)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakCard(streak: Int) {
    RayCollCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    stringResource(R.string.stats_streak),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    pluralStringResource(R.plurals.stats_streak_days, streak, streak),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_local_fire_department_filled),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SrsDistribution(stats: CardStats) {
    val srs = LocalSrsColors.current
    // zawijane: przy dużej czcionce lub długich nazwach (DE) cztery kafelki nie nachodzą na siebie
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        maxItemsInEachRow = SRS_CATEGORY_COUNT
    ) {
        val tile = Modifier.weight(1f)
        SrsStatItem(tile, SrsCardCategory.NEW, R.string.srs_badge_new, stats.newCount, srs.newFg, srs.newBg)
        SrsStatItem(
            tile,
            SrsCardCategory.LEARNING,
            R.string.srs_badge_learning,
            stats.learning,
            srs.learningFg,
            srs.learningBg
        )
        SrsStatItem(tile, SrsCardCategory.REVIEW, R.string.srs_badge_review, stats.review, srs.reviewFg, srs.reviewBg)
        SrsStatItem(
            tile,
            SrsCardCategory.MASTERED,
            R.string.srs_badge_mastered,
            stats.mastered,
            srs.masteredFg,
            srs.masteredBg
        )
    }
}

@Composable
private fun SrsStatItem(
    modifier: Modifier,
    category: SrsCardCategory,
    @StringRes label: Int,
    count: Int,
    foreground: Color,
    background: Color
) {
    Surface(shape = MaterialTheme.shapes.small, color = background, contentColor = foreground, modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ta sama ikona kategorii co w filtrze przeglądania fiszek
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    painterResource(srsCategoryIcon(category)),
                    contentDescription = null,
                    modifier = Modifier.size(StatIconSize)
                )
                Text(count.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            // jedna linia: dłuższa nazwa (np. „Opanowane”, „Wiederholung”) zmniejsza się zamiast łamać słowo
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = StatLabelMinSize,
                    maxFontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            )
        }
    }
}

private val StatIconSize = 16.dp
private const val SRS_CATEGORY_COUNT = 4
private val StatLabelMinSize = 8.sp
