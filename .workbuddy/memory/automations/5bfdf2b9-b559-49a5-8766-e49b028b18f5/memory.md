# 自动化：今日规划 · 每日自我迭代

目标：每天自动给「今日规划」加一个有价值的功能 / 修复，保持界面简洁优雅、交互简单，并跑通自动化测试与打包。

## 执行约定

1. 先读本文件，再读 `.workbuddy/memory/` 最近几天的日志，避免重复做已做过的事。
2. 一次迭代聚焦 1 个主题（功能或修复），顺手补测试。
3. 收尾必做：`./gradlew :app:testDebugUnitTest` + `./gradlew :app:assembleDebug`，更新 README，追加当天日志，提交 commit。
4. 环境：JDK 17 / SDK 34 已配好，`GRADLE_USER_HOME=D:\develop\gradle\.gradle`；Maven 仓库可访问（junit、gson 都能下载）。

## 历史

### 2026-10-31（第 1 次）：数据备份与恢复 + 单元测试基建 → v1.0.53

- 新增 `data/backup/`（BackupPayload / BackupCodec / BackupMapping / BackupManager）+ `util/BackupNames.kt`；7 个 DAO 补全量读写与清空；我的页「数据」分组支持导出 JSON 与「覆盖 / 合并」导入。
- 建立 `app/src/test/java/`（JUnit 4 + Gson），19 条用例覆盖 DateUtils、备份编解码与统计、备份文件名；`build.bat` 增加 `[3] 运行单元测试` 与 `build.bat test`。
- 结果与坑：见 `.workbuddy/memory/2026-10-31.md`（setValue 导入丢失、CRLF/GBK 批处理编辑只能用 Python 字节级改写）。

### 2026-11-01（第 2 次）：年度热力图与年度总览 + 连续天数时区修复 → v1.0.54

- 新增 `util/Heatmap.kt`（纯 Kotlin，无 Android 依赖：等级四档、按周分列网格、月份标签、年度总览、最长/当前连续天数）与 `util/DayKeys.kt`（`yyyy-MM-dd` ↔ 本地 0 点时间戳，java.time）。
- 统计页新增「年度总览」卡与「年度专注热力图」卡（`ui/stats/HeatmapChart.kt`，Canvas 绘制 + 横向滚动自动到最近一周 + 点击格子看当天分钟数 + 图例）。
- `StatsSummary` 扩展 `heatRangeStart/heatRangeEnd/heatByDay/year`；仓库层公开常量 `HEATMAP_DAYS = 365`（测试直接引用）。
- 修复：`getActiveDays` 原用 `(startAt/86400000)*86400000`（UTC 分桶）与 `startOfDay`（本地 0 点）口径不一致 → 东八区连续天数恒为 0。
- 结果与坑：见 `.workbuddy/memory/2026-11-01.md`（**SQLite `strftime('%s')` 带 localtime 不补偿时区，第一版修法仍错**；Windows 上 `time.tzset()` 无效，验证时区逻辑要用固定 epoch）。
- commit 4186834；单测 19 → 38 全绿，包 1.0.54（code 59）。

### 2026-11-01（第 3 次）：一句话建计划 + 统计页入口 → v1.0.55

- `util/QuickPlanParser.kt`（纯 Kotlin）：一句话解析标题/图标/每日分钟/起止日期，"先摘后清 + 等长空格抹除"保证输入顺序无关；支持今天/明天/后天/周几/下周/周末/月日/完整日期/月底/年底/区间/跨度天数；emoji 提图标、关键词兜底、颜色按标题哈希。
- `ui/plan/QuickPlanSheet.kt`：单行输入 + 实时预览 + 示例一键填入 + 回车即存；首页 FAB 加 ⚡ 入口。
- 修复：上一版 `StatsScreen` 无导航入口（功能不可达）→ MainActivity 加 stats 路由 + 我的页「专注统计」入口 + 返回键。
- 测试 38 → 61 全绿（新增 QuickPlanParserTest 23 条）；包 1.0.55（code 60）；commit b899389。

### 2026-11-01（第 4 次）：每日专注目标 + 目标进度环 → v1.0.56

- `util/Goal.kt`（纯 Kotlin）：目标取值约束、进度/百分比/剩余、四档状态、达标天数、周目标、连续达标（今天未达标回落昨天）、按近期中位数建议目标、进度文案。
- 我的 → 「每日目标」分组（chips + ±5 微调 + 一键采用建议值）；统计页顶部目标进度环（Canvas 双弧 + 动画）；本周柱状图加目标虚线参考线与达标高亮。
- 数据：`SettingsStore.dailyGoalMinutes`；`FocusRepository.recentDailyMinutes()`；`StatsViewModel` 改为依赖 settingsStore 并用 `combine` 重算 streak。
- 测试 61 → 82 全绿（新增 GoalTest 21 条）；包 1.0.56（code 62）；commit b0b92d1。

## 下次可选题

- 热力图支持点击某天跳转到当日计划页
- 目标达成提醒：当日达标时发一条通知（复用 Notifier 渠道）
- 热力图按「是否达标」着色（而不只是按最大值分档）
- 计划模板与一键复用
- 日历周视图切换
- 备份定时提醒（如 30 天未备份提示）
- 一句话解析扩展到备注/任务（复用同一套 token 规则）
- 首页顶部搜索框：按标题搜计划与备注
