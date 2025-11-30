---
type: bug
bug_id: BUG-002
date: 2025-11-30
severity: high
status: resolved
resolved_date: 2025-11-30
time_spent: 4h
module: docker/loki
tags:
  - record/bug
  - analysis/root-cause
  - topic/observability
  - topic/loki
  - topic/otel
  - topic/grpc
---

# Grafana 日志卷有数据但日志详情为空

## 问题现象

使用 OTel Agent 启动服务后，Grafana 中：
- **日志卷图表**：显示有数据量（柱状图正常）
- **日志详情区**：显示"没有找到日志"

初始观察到"IDEA 启动不行，JAR 启动可以"的表象，但这是**误导性的**。

## 重现步骤

1. 启动 Docker 环境（Loki + OTel Collector + Grafana）
2. 使用 OTel Agent 启动任意服务
3. 打开 Grafana → Explore → 选择 Loki 数据源
4. 查询近 5 分钟日志，观察到日志卷有数据但详情为空

## 环境

- Loki: 3.x（native OTLP 接入）
- OTel Collector: 0.127.0
- OpenTelemetry Java Agent: 2.22.0
- Grafana: latest

## 根因分析（Five Whys）

| # | 问题 | 答案 |
|---|------|------|
| 1 | 为什么 Grafana 日志详情为空？ | Loki 查询返回了 gRPC 错误，Grafana 前端只显示"无数据" |
| 2 | 为什么 Loki 查询返回错误？ | `ResourceExhausted: trying to send message larger than max (7961782 vs. 4194304)` |
| 3 | 为什么 128KB 存储数据会产生 ~8MB gRPC 响应？ | OTLP 日志的结构化元数据在 Protobuf 序列化时显著膨胀（~62 倍） |
| 4 | **根因** | Loki 默认 gRPC 消息大小限制 **4MB** 对 OTLP 日志场景过小，这是 **Loki 的已知问题** |

### 关键数据对比

| 启动方式 | 日志条数 | 存储大小 | gRPC 响应 | 结果 |
|----------|----------|----------|-----------|------|
| JAR 启动 | 300 条 | 83KB | < 4MB | 成功 |
| IDEA 启动 | 451 条 | 128KB | ~8MB | 失败 |

### 为什么会出现 62 倍膨胀？

OTLP 日志的每条记录携带大量结构化元数据：
- `trace_id`、`span_id`（链路追踪）
- `service.name`、`service.namespace`（资源属性）
- `deployment.environment`、`host.name` 等

这些元数据在 Loki 存储时是**压缩**的，但在 gRPC 内部通信（`/logproto.Querier/Query`）时需要**解压并序列化为 Protobuf**，导致巨大膨胀。

### 为什么"IDEA 不行，JAR 可以"是误导？

- **不是启动方式差异**：与 ClassLoader、热重载无关
- **本质是日志量差异**：IDEA 多次调试重启累积了更多日志（451 vs 300 条）
- **JAR 启动恰好未超限**：刚启动时日志量小，gRPC 响应未超 4MB

## 解决方案

### 即时修复

修改 `docker/loki/loki-config.yaml`，增加 gRPC 消息大小限制到 **16MB**：

```yaml
server:
  http_listen_port: 3100
  grpc_listen_port: 9096
  log_level: info
  # gRPC 消息大小限制（解决 OTLP 日志查询 ResourceExhausted 错误）
  grpc_server_max_recv_msg_size: 16777216  # 16MB
  grpc_server_max_send_msg_size: 16777216  # 16MB

# 查询前端配置
frontend:
  grpc_client_config:
    max_recv_msg_size: 16777216  # 16MB
    max_send_msg_size: 16777216  # 16MB

# 前端 Worker 配置
frontend_worker:
  grpc_client_config:
    max_recv_msg_size: 16777216  # 16MB
    max_send_msg_size: 16777216  # 16MB
```

> [!important] 三处都需要配置
> - `server`: gRPC 服务端限制
> - `frontend`: 查询前端到后端的客户端限制
> - `frontend_worker`: Worker 到调度器的客户端限制

### 为什么选择 16MB？

| 限制值 | 适用场景 |
|--------|----------|
| 4MB（默认） | 传统日志（无结构化元数据） |
| **16MB** | OTLP 日志开发环境（推荐） |
| 50-100MB | 生产环境大规模查询 |

16MB 足够覆盖开发环境的查询需求（~1000 条 OTLP 日志），同时不会过度消耗内存。

### 长期改进

- [x] 增加 gRPC 消息大小限制到 16MB
- [ ] 生产环境考虑启用查询分页，避免单次返回过大响应
- [ ] 监控 Loki gRPC 响应大小，设置告警阈值

## 经验教训

- **学到**: Loki "日志卷有数据但详情为空"现象，往往是**查询阶段**的问题（gRPC 超限、超时），而非数据丢失
- **学到**: OTLP 日志的结构化元数据会导致 gRPC 响应显著膨胀（可达 60+ 倍），Loki 默认 4MB 限制过小
- **学到**: 排查时注意**区分表象和本质**——"IDEA vs JAR"只是日志量差异的表象
- **改进**: 排查 Grafana 日志问题时，**第一步检查 Loki 服务端日志**（`docker logs patra-loki`）

## 调试技巧

```bash
# 查看 Loki gRPC 错误
docker logs patra-loki 2>&1 | grep -i "resourceexhausted"

# 查看查询统计（日志条数、响应大小）
docker logs patra-loki 2>&1 | grep "total_lines\|total_bytes"
```

## 相关资源

- 参考: [Loki Issue #2271 - ResourceExhausted grpc message larger than max](https://github.com/grafana/loki/issues/2271)
- 参考: [Loki Issue #7700 - grpc response size larger than max configured](https://github.com/grafana/loki/issues/7700)
- 文档: [Loki Configuration Reference](https://grafana.com/docs/loki/latest/configure/)
