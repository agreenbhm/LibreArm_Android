package com.ptylr.librearm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ptylr.librearm.R
import com.ptylr.librearm.health.HealthConnectManager
import com.ptylr.librearm.model.HistoricalReading
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

// Fixed anchor so saved pager indices remain stable across launches and month rollovers.
private val ANCHOR_MONTH: YearMonth = YearMonth.of(2000, 1)

private enum class HistoryTab { Calendar, Trends }

@Composable
fun HistoryScreen(
    healthManager: HealthConnectManager,
    hasBloodPressureReadPermission: Boolean,
    hasHeartRateReadPermission: Boolean,
    healthAvailable: HealthConnectManager.Availability,
    permissionPreviouslyDenied: Boolean,
    onRequestReadPermission: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onInstallHealthConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasBloodPressureReadPermission) {
        ReadPermissionRequired(
            modifier = modifier,
            healthAvailable = healthAvailable,
            previouslyDenied = permissionPreviouslyDenied,
            onGrantClick = onRequestReadPermission,
            onOpenHealthConnect = onOpenHealthConnect,
            onInstallHealthConnect = onInstallHealthConnect
        )
        return
    }

    val zone = remember { ZoneId.systemDefault() }
    val monthYearPattern = stringResource(R.string.history_month_year_format)
    val monthYearFormatter = remember(monthYearPattern) { DateTimeFormatter.ofPattern(monthYearPattern) }

    val today = remember { YearMonth.now() }
    val pageCount = remember(today) {
        (ChronoUnit.MONTHS.between(ANCHOR_MONTH, today) + 1).toInt().coerceAtLeast(1)
    }
    val currentMonthPage = pageCount - 1

    val pagerState = rememberPagerState(initialPage = currentMonthPage, pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    val displayedMonth = remember(pagerState.currentPage) {
        ANCHOR_MONTH.plusMonths(pagerState.currentPage.toLong())
    }

    var selectedTab by rememberSaveable { mutableStateOf(HistoryTab.Calendar) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (!hasHeartRateReadPermission) {
            HeartRatePermissionBanner(onOpenHealthConnect = onOpenHealthConnect)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                },
                enabled = pagerState.currentPage > 0
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = stringResource(R.string.history_prev_month)
                )
            }
            Text(
                text = displayedMonth.format(monthYearFormatter),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + 1).coerceAtMost(currentMonthPage)
                        )
                    }
                },
                enabled = pagerState.currentPage < currentMonthPage
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.history_next_month)
                )
            }
        }

        PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == HistoryTab.Calendar,
                onClick = { selectedTab = HistoryTab.Calendar },
                text = { Text(stringResource(R.string.history_tab_calendar)) }
            )
            Tab(
                selected = selectedTab == HistoryTab.Trends,
                onClick = { selectedTab = HistoryTab.Trends },
                text = { Text(stringResource(R.string.history_tab_trends)) }
            )
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .weight(1f)
                .padding(top = 16.dp)
        ) { pageIndex ->
            val pageMonth = remember(pageIndex) { ANCHOR_MONTH.plusMonths(pageIndex.toLong()) }
            var readings by remember(pageMonth) { mutableStateOf<List<HistoricalReading>>(emptyList()) }

            LaunchedEffect(pageMonth) {
                val start = pageMonth.atDay(1).atStartOfDay(zone).toInstant()
                val end = pageMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
                readings = healthManager.readRange(start, end)
            }

            when (selectedTab) {
                HistoryTab.Calendar -> CalendarView(pageMonth, readings, zone)
                HistoryTab.Trends -> TrendsView(pageMonth, readings, zone)
            }
        }
    }
}
