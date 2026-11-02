# patra-analytics-simulator

行为数据的流量模拟器（PAP-171）。真实访客几乎为零，规模靠它造：按真实网站的访问规律生成合成事件（都带 `is_synthetic = true`），灌进管线，再拿自己记的真值对账。设计见 [`docs/patra/specs/2026-11-02-analytics-simulator-design.md`](../../../docs/patra/specs/2026-11-02-analytics-simulator-design.md)。

## 架构概览

```text
流量模型（model）                      两种输出（event）            去向
Zipf 文献热度 + 按刊分主题   ─┐
昼夜 × 周内到达曲线           ├→ SimSession / SimEvent ─┬→ 富化事件 ③ → backfill：直写 ClickHouse events
页面跳转链（马尔可夫）        │                        └→ 采集信封 ② → live：经 HTTP 打采集端点
爬虫三类、1% 重发            ─┘
                                        ↓ 已确认送达的事件
                              真值账本（truth）→ reconcile：按 PAP-170 口径查汇总表对账 → report
```

- **独立命令行**（picocli），不依赖服务内部代码；只在测试里用 api 模块的 JSON Schema 与 COUNTER-Robots 列表、infra 的富化适配器核对一致性。
- **确定性**：随机源由（种子，日期，块号）派生，同参数两次生成逐字节相同；灌历史的批次去重令牌 `backfill:{运行号}:{参数指纹}:{日期}:{块}`，重跑同一条命令不会写出重复。
- **运行号隔离**：设备 ID 前缀 `sim{NNN}`、合成用户 ID 落在 `9×10¹⁸ + 运行号×10⁷` 起的区间，对账只取本次运行的人；注销清理任务跳过这个区间。
- **真值口径**：每个（北京日期，文献）的独立读者，只数真人会话（送达的事件里有浏览器事件、且没有一条被判为爬虫），与 PAP-170 的「看过人数」查询一致。

## 命令

```bash
./gradlew :patra-api:patra-analytics:patra-analytics-simulator:installDist
SIM=patra-api/patra-analytics/patra-analytics-simulator/build/install/patra-analytics-simulator/bin/patra-analytics-simulator
export CLICKHOUSE_PATRA_PASSWORD=…   # 见 ~/.patra/secrets/clickhouse.env，不收命令行参数
```

| 命令 | 例 |
|---|---|
| 灌历史 | `$SIM backfill --run 1 --events 102000000 --days 88 --articles articles.csv --clickhouse-url http://…:8123` |
| 试灌前两天 | 同上加 `--only-days 2`（之后去掉再跑全量，前两天被令牌去重） |
| 实时压测 | `$SIM live --run 2 --rate 1200 --duration 30m --articles articles.csv --collector-url http://…/patra-analytics/events --clickhouse-url … --prometheus-url …` |
| 分级加压 | `live … --ramp 500,1000,1500,2000 --step-length 5m` |
| 对账 | `$SIM reconcile --run 1 --clickhouse-url …`（误差 > 1% 退出码 1） |
| 报告 | `$SIM report --run 1 --run 2 --clickhouse-url … --output docs/patra/reports/v0.18-analytics-load-test.md` |

输出在 `simulator-out/run-NNN/`：`params.json`、`truth.csv.gz`、`summary.json`、`reconcile.json`；实时压测另有 `live-stats.csv`（每秒）、`latency.json`、`parts.csv`（每 30 秒的片段数与新鲜度）、`lag.csv`。退出码：0 成功、1 对账未通过、2 参数错误、3 ClickHouse 磁盘到红线、4 其他错误。

容器内存由 mini 上另开的采样写进运行目录的 `mem.csv`，报告会读它：

```bash
echo "time,container,mem_mib,limit_mib" > mem.csv
while sleep 15; do docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' | awk -v t="$(date -u +%FT%TZ)" 'function mib(x){ if (x ~ /GiB$/) return x*1024; if (x ~ /KiB$/) return x/1024; if (x ~ /MiB$/) return x+0; return x/1048576 } { print t "," $1 "," mib($2) "," mib($4) }' >> mem.csv; done
```

在 mini 上执行的完整步骤（文献池导出、试灌、正式灌、压测、对账、报告）见 spec §12，每一步都先征得用户同意。

## 测试

- 单元：流量模型（Zipf、到达曲线、跳转链约束、爬虫与频率规则复刻、1% 重发）、两种输出逐条过 JSON Schema、UA / IP 表与服务富化结果一致、真值、灌历史分块与令牌、发送重试、命令行。
- 集成（Testcontainers ClickHouse，服务自己的建表迁移）：灌历史后对账误差为 0、重跑幂等、试灌后全量与一次跑完相同、每天一个分区、parts 与新鲜度采样。
- 端到端（`patra-analytics-boot` 的 `e2eTest`：`LiveSimulationE2E`）：真的 analytics 服务 + Testcontainers（PG、ClickHouse、Kafka、Redis），实时压测 60 秒后对账。
