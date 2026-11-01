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

### 2026-11-01（第 5 次）：计划模板与一键复用 → v1.0.57

- `util/PlanTemplate.kt`（纯 Kotlin + Gson + java.time）：模板只存配置不存日期，复用时从今天重算区间；`signature(title|分钟|天数|icon)` 当 id 去重，`merge` 命中即 useCount+1，排序后 `take(24)` 自动淘汰最不常用。
- 存储：`SettingsStore` 增 `plan_templates` JSON key + `planTemplates` Flow + `remember/removePlanTemplate`。
- UI：`ui/plan/PlanTemplateBar.kt`（横向 chip，点击填入 / 长按删除）+ AddPlanSheet 与 QuickPlanSheet 顶部接入并在保存时自动沉淀；长按计划卡加「存为模板」；我的页加「计划模板」分组。
- 测试 82 → 102 全绿（新增 PlanTemplateTest 20 条）；包 1.0.57（code 63→64）。
- 坑：daily slider 原上限 180 会让模板 480 分钟被夹，已统一放宽到 `PlanTemplates.MAX_MINUTES`。模板在 DataStore 里，**不随备份 JSON 走**。

### 2026-11-01（第 6 次）：全局搜索（计划 + 备注） → v1.0.58

- `util/Search.kt`（纯 Kotlin）：分词 + 四档打分（全等/前缀/词首/包含，位置惩罚封顶 30）+ 高亮区间合并 + 摘要截取 + 带字段权重的 `rank`（标题权重 3 > 正文 1，返回 fieldIndex）。
- `HomeViewModel` 接入 `noteRepository`，新增 `query` 与 `searchResults` 流；`HomeSearchHit`（PlanHit / NoteHit）；`focusPlan` 翻到计划生效期内离今天最近的一天。
- 新增 `ui/home/SearchOverlay.kt`（内联搜索框自动聚焦 + 结果卡片高亮/摘要）；首页顶栏 🔍 入口、搜索态隐藏 FAB、返回键先退搜索。
- 测试 102 → 126 全绿（新增 SearchTest 24 条）；包 1.0.58（code 65）；commit e65a2db。
- 坑：给 Scaffold 内容 Column 套 `if/else` 用 Python 按行号区间改缩进最稳，闭合括号常落在区间外需单独补；`snippet` 换行 1:1 替换下标不变，不必做偏移修正。

### 2026-11-01（第 7 次）：专注节律（时段分布 + 黄金时段 + 工作日/周末对比） → v1.0.59

- `util/Rhythm.kt`（纯 Kotlin）：24 小时归一化、6 个 4 小时时段划分与名称区间、时段聚合/占比、黄金时段、峰值小时（降序、平局取小时小的）、一句话总结、周末判定、区间内工作日/周末天数、按天拆分与日均、对比文案。
- `FocusRecordDao.sumByHour`（`CAST(strftime('%H',...,'localtime') AS INTEGER)` + 投影 POJO `HourTotal`）；`FocusRepository.rhythmByHour()`；`StatsSummary.rhythmByHour`，区间复用 `RHYTHM_DAYS = HEATMAP_DAYS`。
- `ui/stats/RhythmChart.kt`：24 小时柱状（峰值小时 tertiary 高亮）+ 六大时段占比条 + 结论文案 + 工作日/周末对比，接入 StatsScreen；聚合全部 `remember` 缓存。
- 测试 126 → 152 全绿（新增 RhythmTest 26 条）；包 1.0.59（code 66）。
- 坑：const val 不允许前向引用（`RHYTHM_DAYS` 必须写在 `HEATMAP_DAYS` 之后）；`strftime('%H')` 要 CAST 成 INTEGER。

### 2026-11-01（第 8 次）：备份完整性 v2 —— 设置与模板入备份 + 备份体检 → v1.0.60

- `util/BackupHealth.kt`（纯 Kotlin）：距上次备份自然日天数、四档分级（NEVER / FRESH<14 / SOON 14–29 / OVERDUE≥30）、状态与建议文案、提醒判断、进度与倒计时文案。
- 备份升到 **schema 2**：`BackupPayload` 增 `settings: BackupSettings?` 与 `templates: List<BackupTemplate>`；旧 v1 备份仍可导入（缺失项自动跳过）。`BackupSettings` 独立定义在 backup 包，避免把 DataStore 依赖带进纯 JVM 单测。
- `BackupManager` 增可选 `settingsStore`：覆盖导入整体还原设置与模板，合并导入只补本机没有的模板（设置不动）。`SettingsStore` 增 `lastBackupAt` / `markBackupExported` / `applyBackupSettings` / `replacePlanTemplates` / `mergePlanTemplates`。
- UI：我的 → 数据卡片顶部「备份状态」块（状态 + 分级 + x/30 天进度条 + 建议文案）；导出成功后记录备份时间。
- 测试 152 → 181 全绿（BackupHealthTest 23 + codec 3 + payload 3）；包 1.0.60（code 67→68）；commit cc8fd08。
- 坑：`withContext` 非 inline，不能用 `return` 提前返回；同包扩展函数不要 import；模板类型转换点只保留在 BackupManager 一处；进度条用两个 Box 自绘以避开 material3 1.2 的 API 不确定性。

### 2026-11-01（第 9 次）：成就殿堂（12 枚徽章 + 徽章墙） → v1.0.61

- `util/Achievement.kt`（纯 Kotlin + Gson）：`AchievementDef/Stats/State` + 12 枚徽章（铜 5 / 银 5 / 金 2）；规则核心是**解锁不可逆**（解锁只认持久化记录，进度回落不掉徽章）。
- `snapshot` 排序：已解锁（解锁时间倒序）→ 未解锁按完成度倒序 → 定义顺序；`nextUp` 排除「已满 100% 但未落库」的项，`motivationText` 因此多一条「继续保持，新的徽章即将点亮」。
- 数据层：`FocusRecordDao` 增 `countActiveDays()` 与 `bestDay()`（返回 `List<DayTotal>`）；`StatsSummary` 增 `totalPomos / allTimeActiveDays / allTimeLongestStreak / bestDayMinutes`（**全量口径**）；适配器做成顶层函数 `data/repo/achievementStatsOf(summary)`，便于 JVM 单测直接调用。
- `SettingsStore` 增 `achievements` Flow + `readAchievements()` + `unlockAchievements()`；`StatsViewModel.refresh()` 顺带结算，新解锁写入并发 `freshIds`。
- UI：`ui/stats/AchievementsScreen.kt`（总览卡 + 两列徽章墙 + 🎉 新解锁横幅）；入口：统计页右上角奖杯、统计页底部入口卡、我的页 ActionRow；路由 `achievements`。
- 测试 181 → **210 全绿**（AchievementTest 25 + AchievementStatsTest 4）；包 1.0.61（code 68→69）；commit 02ad6e4。
- 坑：扩展图标 `Icons.Default.EmojiEvents` **必须单独 import** `androidx.compose.material.icons.filled.EmojiEvents`；确认图标存在用 `unzip -l classes.jar | grep` 最快。
- 未做（有意留到下次）：成就未进备份 JSON。

### 2026-11-01（第 10 次）：本周回顾（上周同期对比 + 趋势徽标 + 整周推算） → v1.0.62

- 新增 `util/WeeklyReview.kt`（纯 Kotlin）：`WeekReview` + `of(thisWeek, lastWeek, todayIndex, goal)`；核心口径是**只比已过去的天**（`elapsed` 夹到 1~7），避免周一拿 1 天比上周整周。文案（badge/delta/span/metric/projection）全在工具内，`goal<=0` 时把「达标 x/y」换成「活跃 x/y」。
- 数据层：`StatsSummary` 增 `lastWeekDaily`；`summary()` 把本周 7 次 `sumWorkSeconds` 合并成**一次** `sumByDay`（覆盖两周）再按日期串拆分。
- UI：`StatsScreen` 在「本周每日分布」下方加 `WeekReviewCard`（大数字 + 趋势徽标 + 双条对比 + 指标行 + 推算），两周皆空时收成一行提示；`TrendBadge` 只在上涨用主题色。
- 测试 210 → **234 全绿**（WeeklyReviewTest 24 条）；包 1.0.62（code 70→71）。

### 2026-11-01（第 11 次）：月度回顾（整月日历 + 上月同期对比） → v1.0.63

- 新增 `util/MonthReview.kt`（纯 Kotlin + java.time）：MonthKey / MonthDay / MonthReport / MonthReview；同期口径「只比上月同一区间」，着色五档（有目标按占比、无目标按绝对分钟），6 段文案全部收在工具里。
- 数据层新增 `FocusRepository.dailyMinutes(start,end)`；`MonthReviewViewModel` 一次取 12 个月逐日数据，翻月不查库。
- 新增 `ui/stats/MonthReviewScreen.kt`（月份切换条 + 汇总卡 + 7 列日历可点选 + 图例）；统计页「本周回顾」下方加入口卡、我的页加入口、路由 `month_review`。
- 测试 234 → 270 全绿；包 1.0.63（code 72）；commit 见 git log。
- 坑：`"$label，"` 中文紧跟插值会被吞进标识符，必须 `${label}`。

### 2026-11-01（第 12 次）：成就入备份（schema 3）+ 导入结果补徽章提示 → v1.0.64

- 新增 `data/backup/BackupAchievements.kt`（纯 Kotlin）：快照排序（解锁时间倒序、同刻 id 升序）、`toRecord`（同 id 取最早时间、未知 id 保留）、`restore`（**空 incoming 一律不动本机**，覆盖式替换 / 合并式只补缺失）、`addedCount`、`countText`。
- 备份升 schema 3：`BackupPayload.achievements` + `totalCount/describe` 计入成就 + `hasAchievements()`；`BackupManager.restore` 返回 `RestoreResult`；UI 提示「徽章 M 枚按备份还原 / 补充 M 枚徽章」，成就页加「解锁记录随备份保存」。
- 测试 270 → 294 全绿；包 1.0.64；commit 70c9320。
- 坑：`git add -A` 会连带 `.gradle/`、`developgradle.gradle/`、`video/` 导致提交 128 失败 → 只加 `README.md version.properties app/src`。

### 2026-11-01（第 13 次）：计划投入统计（连续达标 + 14 天趋势） → v1.0.65

- `util/PlanInsight.kt`（纯 Kotlin）：单点派生（minutes/achieved/ratio/barRatio/overshoot）+ 画像（streak/bestStreak/total/activeDays/achievedDays/average）+ 文案（headline/todayText/totalText/averageText/rateText）。
- 三条口径：窗口右端锚 `min(今天, 计划结束日)`（结束的计划仍看最后 14 天）；只统计计划有效期内的日子；连续达标今天没达标才回落昨天，**计划结束后不回落**；最长连续回溯 90 天。
- 数据：`PlanDao.getLogsOfPlanBetween` + `PlanRepository.logsOfPlan`，弹层只查一次库。
- UI：`ui/plan/PlanInsightSheet.kt`（今日进度条 + 三数字 + 14 天柱状图 + 点柱看详情）；入口：点计划卡（原先无响应）+ 长按菜单「查看投入统计」。
- 测试 294 → 326 全绿（PlanInsightTest 32）；包 1.0.65（74→75，二次打包后文件为 76）。
- 坑：`verticalScroll` 里 `fillMaxHeight` 无有界高度；「几号」单独用 `SimpleDateFormat("d")`；今日秒数要覆盖不要相加；测试先确认窗口边界。

### 2026-11-01（第 14 次）：成就详情与达成预测 → v1.0.66

- 新增 `util/AchievementForecast.kt`：7 种预测类型（已解锁 / 待结算 / 按节奏 / 停滞 / 峰值 / 连续 / 无数据）；累积型按**近 14 天（不含今天）日均增量**外推，峰值型与连续型只给实话不给天数；`nextLine` 供总览与统计页复用。
- 数据：`FocusRepository.recentAchievementStats(days)`（3 次聚合查询）+ 纯函数 `achievementStatsFrom(byDay, pomos, byHour)`；`StatsViewModel.recentStats`。
- UI：`ui/stats/AchievementDetailSheet.kt`（点徽章看条件 / 进度 / 预测 / 同类徽章）；徽章卡整卡可点；总览与统计页激励文案带上「约 N 天」。
- 顺手修：统计页成就入口卡空态重复渲染同一句文案。
- 测试 326 → 355 全绿；包 1.0.66（code 78）；commit ead0c2f。
- 坑：整数外推不能先算日均（浮点误差 +1 天）；`OverviewCard` 参数与局部变量同名会被遮蔽。

### 2026-11-01（第 15 次）：「现在」建议条 → v1.0.67

- 新增 `util/NowAdvice.kt`：六种建议按固定优先级（无计划 → 计时中 → 全部达成 → 深夜收工 → 全新开始 → 最接近达标），先易后难、22:30 后不催进度；附带 `DateUtils.minuteOfDay`。
- UI：`ui/home/NowStrip.kt` 一条玻璃卡建议条插在日历星期表头上方，可一键开计时 / 打开今天 / 一句话建计划；底部细进度线；每分钟自动刷新时钟。
- 只复用首页已有数据，无新表无新查询；首帧用 `shownMonth > 0` 挡掉空态闪烁。
- 测试 355 → 376 全绿；包 1.0.67（code 80）。

## 下次可选题

- 成就解锁时发通知（复用 Notifier 渠道，详情弹层给开关）
- 成就卡导出成图片分享
- 成就页按「已点亮 / 进行中」筛选
- 月历页支持左右滑动手势切月
- 热力图 / 月历点击某天跳转到当日卡片页
- 周回顾卡点击展开上周逐日对比（哪几天掉队）
- 备份「导入前体检」：与本机逐类对比，先告诉用户会多 / 少多少条
- 目标达成提醒：当日达标时发一条通知
- 计划投入弹层里点某天 → 跳到当日卡片页
- 计划卡角落显示连续达标小徽标（≥2 天才出现，避免噪音）
- 在投入弹层里直接调每日目标（±5 分钟），实时预览达标变化

