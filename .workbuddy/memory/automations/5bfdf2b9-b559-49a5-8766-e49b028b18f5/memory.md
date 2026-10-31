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

## 下次可选题

- 把计划模板并入备份 JSON（当前备份恢复后模板会丢）

- 热力图支持点击某天跳转到当日计划页
- 目标达成提醒：当日达标时发一条通知（复用 Notifier 渠道）
- 热力图按「是否达标」着色（而不只是按最大值分档）
- 日历周视图切换
- 备份定时提醒（如 30 天未备份提示）
- 一句话解析扩展到备注/任务（复用同一套 token 规则）
- 首页顶部搜索框：按标题搜计划与备注
