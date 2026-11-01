package com.focuslist.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.repo.achievementStatsOf
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.util.AchievementState
import com.focuslist.app.util.AchievementTier
import com.focuslist.app.util.Achievements
import kotlinx.coroutines.delay

/** 「新解锁」提示横幅的展示时长 */
private const val FRESH_BANNER_MILLIS = 6000L

/**
 * 成就殿堂：把累计时长、番茄、连续天数等长期指标变成可收集的徽章。
 *
 * 顶部是总览（已解锁数量 + 整体完成度 + 最近解锁），下面是两列徽章墙，
 * 未解锁的按完成度排在前面，先展示「快要拿到」的，激励感最强。
 *
 * @param container 依赖容器
 * @param onBack 返回上一页回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(container: AppContainer, onBack: () -> Unit = {}) {
    val viewModel: StatsViewModel = viewModel {
        StatsViewModel(container.focusRepository, container.settingsStore)
    }
    val summary by viewModel.summary.collectAsState()
    val unlocked by viewModel.achievements.collectAsState()
    val freshIds by viewModel.freshIds.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    // 「新解锁」提示展示几秒后自动收起
    LaunchedEffect(freshIds) {
        if (freshIds.isNotEmpty()) {
            delay(FRESH_BANNER_MILLIS)
            viewModel.clearFreshIds()
        }
    }

    // 成就快照随数据与解锁记录变化重算，滚动/重组时不会重复计算
    val states = remember(summary, unlocked) {
        Achievements.snapshot(achievementStatsOf(summary), unlocked)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("成就殿堂", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OverviewCard(states = states, freshIds = freshIds)

            // 两列徽章墙：不引入 Lazy 网格，12 项规模下普通行列最省心
            states.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { state ->
                        AchievementCard(state = state, modifier = Modifier.weight(1f))
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 总览卡：解锁数量、整体完成度、最近解锁的徽章与一句激励文案。
 *
 * @param states 全部成就状态
 * @param freshIds 本次新解锁的成就 id
 */
@Composable
private fun OverviewCard(states: List<AchievementState>, freshIds: List<String>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("成就殿堂", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = Achievements.countText(states),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 最近解锁：取解锁时间最新的 6 个，没有则给一句引导
            val recent = states.filter { it.unlocked }.take(6)
            if (recent.isEmpty()) {
                Text(
                    text = "还没有徽章，先完成一次专注吧",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    recent.forEach { state ->
                        Text(text = state.def.emoji, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            val overall = Achievements.overallPercent(states)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(
                    fraction = overall / 100f,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$overall%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = Achievements.motivationText(states),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 只在这种「已经有徽章」的场景提示一次，空态下不多占一行
            if (recent.isNotEmpty()) {
                Text(
                    text = "解锁记录随备份一起保存，换机后解锁日期不丢",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            if (freshIds.isNotEmpty()) {
                val names = freshIds.mapNotNull { id ->
                    states.firstOrNull { it.def.id == id }?.def?.title
                }
                if (names.isNotEmpty()) {
                    Text(
                        text = "🎉 新解锁：${names.joinToString("、")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 单个成就徽章卡：图标 + 稀有度 + 名称 + 条件 + 进度条 + 状态文案。
 *
 * @param state 成就状态
 * @param modifier 外部布局修饰
 */
@Composable
private fun AchievementCard(state: AchievementState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                // 未解锁整体压暗，让已解锁的徽章自然成为视觉焦点
                .alpha(if (state.unlocked) 1f else 0.62f)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = state.def.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = Achievements.tierText(state.def.tier),
                    style = MaterialTheme.typography.labelSmall,
                    color = tierColor(state.def.tier)
                )
            }

            Text(text = state.def.title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = state.def.desc,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                lineHeight = 15.sp
            )

            ProgressBar(
                fraction = state.percent / 100f,
                color = if (state.unlocked) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = if (state.unlocked) {
                    "已解锁 · ${Achievements.unlockDateText(state.unlockedAt)}"
                } else {
                    Achievements.progressText(state)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (state.unlocked) scheme.primary else scheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

/**
 * 自绘进度条：两个 Box 叠加，避免依赖进度组件的具体签名。
 *
 * @param fraction 进度 0-1
 * @param color 已完成部分颜色
 * @param modifier 外部布局修饰
 */
@Composable
private fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                // 留 1% 兜底宽度，进度为 0 时也能看到圆角底衬而不是一条空线
                .fillMaxWidth(fraction.coerceIn(0f, 1f).coerceAtLeast(0.01f))
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

/**
 * 稀有度配色：铜牌用次要色、银牌用主色、金牌用第三色。
 *
 * @param tier 稀有度
 * @return 主题色
 */
@Composable
private fun tierColor(tier: AchievementTier): Color {
    val scheme = MaterialTheme.colorScheme
    return when (tier) {
        AchievementTier.BRONZE -> scheme.secondary
        AchievementTier.SILVER -> scheme.primary
        AchievementTier.GOLD -> scheme.tertiary
    }
}
