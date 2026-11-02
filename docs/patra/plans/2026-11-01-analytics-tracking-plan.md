# 埋点方案 JSON Schema 实施计划（PAP-164）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（本仓库约定：plan 在当前会话内联执行，不派子代理逐任务实现）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把埋点方案落成机器可校验的 JSON Schema：三种形态（浏览器批次、采集信封、富化事件）、dlq 记录、10 个事件的属性，外加合格 / 不合格样例与 COUNTER-Robots 副本。

**Architecture:** 公共类型放 `defs`，每个事件的属性一个文件，`event-catalog` 把事件名、来源、版本、属性绑成分支；三种形态各自引用目录。样例按「schema 名--用例名」命名，一个只在本机运行的校验脚本把所有 schema 与样例跑一遍：合格的必须通过，不合格的必须失败。

**Tech Stack:** JSON Schema 2020-12；校验用 Python `jsonschema` 4.23（`uv run --with`，脚本放会话 scratchpad，不进仓库）。PAP-167 会用 Java 校验器把同一批样例变成仓库里的测试。

**Spec:** [`docs/patra/specs/2026-11-01-analytics-tracking-plan-design.md`](../specs/2026-11-01-analytics-tracking-plan-design.md)

## Global Constraints

- 目录：`patra-api/patra-analytics/patra-analytics-api/src/main/resources/analytics/`（`schema/`、`examples/valid/`、`examples/invalid/`、`robots/`），本 Issue 不建 Gradle 模块。
- 每个 schema 的 `$schema` 为 `https://json-schema.org/draft/2020-12/schema`，`$id` 为 `https://patra.linqibin.dev/schemas/analytics/<相对 schema/ 的路径>`，相互引用用相对路径。
- 对象一律封闭：叶子对象 `additionalProperties: false`；用了 `oneOf` / `allOf` 组合的对象用 `unevaluatedProperties: false`。
- 不依赖 `format` 关键字（2020-12 默认只当注解、不校验）：时间、UUID 一律用 `pattern`。
- 时间：`^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}Z$`（UTC、毫秒）。
- UUID：小写，`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`。
- `article_id`、期刊 ID：`^[1-9][0-9]{0,18}$` 的字符串；`user_id` 同一规则或 `null`。
- `device_id`：`^[A-Za-z0-9_-]{43}$`。
- 版本：事件文件用注解 `"x-patra-version": "1-0-0"` 记当前版本；目录分支里 `schema_version` 的约束是 `^1-[0-9]+-[0-9]+$`（接受同一 MODEL，spec §7）。
- ③ 富化事件与 dlq 记录里不得出现 `client_ip`、`user_agent`。
- 字段名 snake_case；枚举值照 spec §3.3 原样（`csl-json`、`gb-t-7714-2015` 等带横线的值不改写）。

## Review Focus

1. **时间写成不带毫秒或带时区偏移**（`2026-11-01T08:30:12Z`、`2026-11-01T16:30:12.000+08:00`）——spec 规定 UTC 毫秒，应判不合格；只靠 `format: date-time` 会放过。由 Task 1 的反例 `collect-envelope--created-at-no-millis.json`、`--created-at-offset.json` 钉住。
2. **数字型 ID**（`"article_id": 123456`）——JS 超过安全整数会丢精度，必须是字符串。由 Task 1 的反例 `collect-envelope--article-id-number.json` 钉住。
3. **`page_path` 带查询串**（`/papers?q=cancer`）——检索词会绕过服务端口径进到浏览器事件里。pattern `^/[^?#]*$`。由 Task 2 的反例 `browser-batch--page-path-query.json` 钉住。
4. **组合 schema 里多出的顶层字段被放过**（`oneOf` 分支各自封闭但顶层没封）——要用 `unevaluatedProperties: false`。由 Task 1 的反例 `collect-envelope--extra-top-level.json` 钉住。
5. **富化事件里残留 IP**——模拟器灌历史直接写 ③，若 schema 允许 `client_ip`，IP 会进库。由 Task 3 的反例 `enriched-event--has-client-ip.json` 钉住。

---

### Task 1：校验脚本、公共类型、10 个事件与采集信封

**Files:**
- Create（scratchpad，不进仓库）：`<scratchpad>/v018/check_schemas.py`
- Create：`schema/defs.schema.json`、`schema/events/<event_name>.schema.json`（10 个）、`schema/event-catalog.schema.json`、`schema/collect-envelope.schema.json`
- Create：`examples/valid/collect-envelope--*.json`、`examples/invalid/collect-envelope--*.json`

**Interfaces:**
- Produces：
  - `defs.schema.json#/$defs/`：`timestamp`、`uuid`、`article_id`、`entity_id`（期刊等，同 article_id 规则）、`user_id`（entity_id 或 null）、`device_id`、`page_type`、`list_type`。
  - `event-catalog.schema.json#/$defs/server_event`（`oneOf` 5 个分支）与 `#/$defs/client_event`（`oneOf` 5 个分支）。每个分支只约束三个键：`event_name`（`const`）、`schema_version`（MODEL 为 1 的 pattern）、`properties`（`$ref` 到 `events/<name>.schema.json`），并 `required` 这三个键。
  - `collect-envelope.schema.json`：spec §4.2 的 16 个字段全部必填；`oneOf` 两支：`{source: const "server"} + $ref server_event` 与 `{source: const "client"} + $ref client_event`；顶层 `unevaluatedProperties: false`。
  - 样例命名：`<schema 文件名去掉 .schema.json>--<用例>.json`，校验脚本据此找 schema。

- [ ] **Step 1：写校验脚本**

  `check_schemas.py <analytics 目录>`：读 `schema/` 下全部文件建 `referencing.Registry`（按 `$id`）；每个 schema 先 `Draft202012Validator.check_schema`；`valid/` 里每个样例必须 0 个错误，`invalid/` 里每个必须 ≥ 1 个错误；样例对应的 schema 不存在算失败。输出每个文件的结果，任一失败退出码 1。

- [ ] **Step 2：先写样例**

  合格（`valid/`）：10 个事件各一条（服务端 5 个 `source: server`、浏览器 5 个 `source: client`），外加 `--logged-out`（`user_id: null`）、`--search-exact-pmid`（`mode: exact_pmid`）、`--search-filters-full`（`filters` 用全所有键）、`--version-addition`（`schema_version: "1-0-3"`）。

  不合格（`invalid/`，文件名即违反的规则）：`--missing-event-id`、`--uuid-uppercase`、`--unknown-event`、`--source-mismatch`（`article_viewed` 配 `client`）、`--version-model-2`（`2-0-0`）、`--extra-property`（`properties` 多一个键）、`--extra-top-level`、`--article-id-number`、`--article-id-leading-zero`（`"0123"`）、`--created-at-no-millis`、`--created-at-offset`、`--device-id-short`（42 位）、`--citation-ids-empty`、`--citation-ids-101`、`--search-unknown-filter`、`--search-venue-21`、`--sort-relevance`、`--format-underscore`（`csl_json`）、`--page-type-unknown`、`--missing-is-synthetic`、`--page-path-query`。

- [ ] **Step 3：运行，确认失败**

  Run：`uv run --with jsonschema==4.23.0 python -I <scratchpad>/v018/check_schemas.py patra-api/patra-analytics/patra-analytics-api/src/main/resources/analytics`
  Expected：FAIL，每个样例报「schema 不存在」。

- [ ] **Step 4：写 schema**

  按 spec §3.2、§3.3、§4.2 写 `defs`、10 个事件属性、`event-catalog`、`collect-envelope`。取值照抄 spec：`query` ≤ 200 字符；`filters` 的 `author` ≤ 200，`type` / `evidence` / `lang` / `venue` 各 ≤ 20 项且 `uniqueItems`，`oa` 只能为 `true`；`page` ≥ 1；`result_count` ≥ 0；`article_count` 1～5000；`article_ids` 1～100 项；`position` ≥ 1；`visible_seconds` ≥ 10；`page_path` 为 `^/[^?#]*$` 或 `null`；`referrer_host` 为不含 `/` 的主机名或 `null`。`page_viewed` 的属性是空对象（`maxProperties: 0`）。

- [ ] **Step 5：运行，确认通过**

  Run：同 Step 3。Expected：全部 PASS，退出码 0。

### Task 2：浏览器批次

**Files:**
- Create：`schema/browser-batch.schema.json`
- Create：`examples/valid/browser-batch--*.json`、`examples/invalid/browser-batch--*.json`

**Interfaces:**
- Consumes：`defs`、`event-catalog#/$defs/client_event`。
- Produces：`browser-batch.schema.json`：`{sent_at, events[]}`，`events` 1～50 项；每项必填 `event_id`、`event_name`、`schema_version`、`created_at`、`page_path`、`page_type`、`properties`，并 `allOf` 引用 `client_event`，`unevaluatedProperties: false`。批次大小 48 KiB 不在 schema 里（JSON Schema 量不了字节），由 PAP-173 的门户代码检查。

- [ ] **Step 1：先写样例**

  合格：`--mixed`（5 种浏览器事件各一条）、`--single`。不合格：`--empty-events`、`--51-events`、`--server-event`（含 `article_viewed`）、`--has-device-id`（浏览器自带身份字段）、`--has-source`、`--page-path-query`、`--missing-sent-at`。

- [ ] **Step 2：运行，确认失败**（Run 同 Task 1 Step 3；Expected：`browser-batch` 样例报 schema 不存在）

- [ ] **Step 3：写 `browser-batch.schema.json`**

- [ ] **Step 4：运行，确认全部通过**

### Task 3：富化事件与 dlq 记录

**Files:**
- Create：`schema/enriched-event.schema.json`、`schema/dlq-record.schema.json`
- Create：对应 `examples/valid/`、`examples/invalid/` 样例

**Interfaces:**
- Consumes：`defs`、`event-catalog`。
- Produces：
  - `enriched-event.schema.json`：采集信封去掉 `client_ip`、`user_agent`，加上 spec §4.3 的 9 个字段（`received_at`、`derived_at`、`country_code` 为 `^[A-Z]{2}$` 或 `null`、`province` 字符串或 `null`、`browser_family`、`os_family`、`device_class`、`is_bot`、`bot_reason` 为 `ua_list` / `ua_class` / `rate` 或 `null`），全部必填；同样按 `source` 分两支引用目录。不复用 `collect-envelope` 文件（两边字段集不同，`unevaluatedProperties` 下继承会把 IP 带进来），公共字段从 `defs` 取。
  - `dlq-record.schema.json`：spec §5 的 5 个字段；`reason_code` 为 `schema_invalid` / `unknown_event` / `source_mismatch` / `version_rejected`；`reason_detail` ≤ 1000 字符；`event_name` 字符串或 `null`；`raw` 字符串 ≤ 16384 字符；另加 `raw_truncated`（boolean）表示是否截断。

- [ ] **Step 1：先写样例**

  `enriched-event` 合格：`--server-article-viewed`、`--client-impressed`、`--bot-ua-list`、`--abroad`（`country_code: "US"`、`province: null`）；不合格：`--has-client-ip`、`--has-user-agent`、`--missing-derived-at`、`--bot-reason-unknown`、`--country-lowercase`。
  `dlq-record` 合格：`--schema-invalid`、`--unknown-event-null-name`；不合格：`--reason-unknown`、`--missing-raw`。

- [ ] **Step 2：运行，确认失败**

- [ ] **Step 3：写两个 schema**

- [ ] **Step 4：运行，确认全部通过**

### Task 4：COUNTER-Robots 副本、核对与提交

**Files:**
- Create：`robots/counter-robots.json`、`robots/README.md`
- Modify：`docs/patra/specs/2026-11-01-analytics-tracking-plan-design.md`（只在 §9 补实际文件清单与样例数，如有出入）

- [ ] **Step 1：取列表**

  从 `https://github.com/atmire/COUNTER-Robots` 的默认分支取 `COUNTER_Robots_list.json`（下载前先看仓库的许可证，确认可以原样再分发；不允许就停下，回报用户），原样存为 `robots/counter-robots.json`。`robots/README.md` 写来源仓库、提交 SHA、取得日期、许可证、更新方法（重新取同一文件并提交）。

- [ ] **Step 2：核对列表是合法 JSON、每项有 `pattern` 字段**

  Run：`python3 -I -c "import json,sys;d=json.load(open(sys.argv[1]));assert all('pattern' in x for x in d);print(len(d))" <robots 路径>`
  Expected：打印条目数，无异常。

- [ ] **Step 3：全量再跑一次校验脚本**，Expected：全部 PASS。

- [ ] **Step 4：对照 spec 逐节核对**（§3.2 十个事件、§3.3 每个属性、§4.2 / §4.3 每个字段、§5 dlq 字段都在 schema 里出现，取值一致），出入处改 schema 或补 spec §9。

- [ ] **Step 5：提交（等用户同意）**

  ```bash
  git add patra-api/patra-analytics/patra-analytics-api/src/main/resources/analytics \
          docs/patra/specs/2026-11-01-analytics-tracking-plan-design.md \
          docs/patra/plans/2026-11-01-analytics-tracking-plan.md \
          docs/patra/release-specs/v0.18-analytics.md
  git diff --cached --stat   # 只含上面这些路径
  git commit -m "feat(analytics): 定下埋点方案与事件 JSON Schema (PAP-164)"
  ```
