# COUNTER-Robots 列表

采集端点按这份列表识别爬虫 UA（埋点方案 §6.7 规则 `ua_list`）。运行时不联网，只读这里的副本。

| 项 | 值 |
|---|---|
| 来源 | [atmire/COUNTER-Robots](https://github.com/atmire/COUNTER-Robots) 的 `COUNTER_Robots_list.json` |
| 来源提交 | `87325c9bd16f0d9e2750b02c3e9a3a9eb7aec8a3`（`master`，仓库最后推送 2024-04-22） |
| 取得日期 | 2026-11-01 |
| 条目数 | 327 |
| 许可证 | MIT，原文见同目录 `LICENSE`（Copyright (c) 2018 COUNTER ONLINE METRICS） |

`counter-robots.json` 与来源文件逐字一致，只有提交时 pre-commit 去掉了两处字符串外的行尾空格（第 489、921 行）。每项是 `{ "pattern": 正则, "last_changed": 日期 }`，`pattern` 按不区分大小写匹配 UA。

**更新**：在来源仓库取最新提交的同一文件与 `LICENSE`，覆盖这两个文件，改上表的来源提交、取得日期与条目数，一起提交。
