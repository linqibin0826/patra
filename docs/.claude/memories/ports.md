# 端口速查表

> 快速查找项目所有服务端口。详细文档见 `docs/designs/infrastructure/01-port-allocation.md`

## 常用端口速查

| 端口 | 服务 | 协议 | 用途 |
|------|------|------|------|
| **3000** | Grafana | HTTP | 可视化面板 |
| **3100** | Loki | HTTP | 日志查询 |
| **3200** | Tempo | HTTP | 链路追踪 |
| **4317** | OTel Collector | gRPC | OTLP 遥测接收 |
| **4318** | OTel Collector | HTTP | OTLP 遥测接收 |
| **6000** | Registry | HTTP | 元数据服务 |
| **6100** | Ingest | HTTP | 数据采集服务 |
| **6300** | Catalog | HTTP | 目录服务 |
| **8848** | Nacos | HTTP | 服务注册/配置 |
| **9090** | Prometheus | HTTP | 指标存储 |
| **9528** | Gateway | HTTP | API 网关 |
| **13306** | MySQL | TCP | 数据库 |
| **16379** | Redis | TCP | 缓存 |
| **19000** | MinIO | HTTP | 对象存储 |

## 按服务分类

### 业务微服务
- Gateway: `9528`
- Registry: `6000`
- Ingest: `6100`
- Catalog: `6300`

### 基础设施
- MySQL: `13306`
- Redis: `16379`
- Nacos: `4000`(控制台) / `8848`(服务) / `9848-9849`(gRPC)
- MinIO: `19000`(API) / `19001`(控制台)

### 消息队列
- RocketMQ NameServer: `9876`
- RocketMQ Broker: `10909` / `10911` / `10912`
- RocketMQ Proxy: `7071`(HTTP) / `8081`(gRPC)
- RocketMQ Dashboard: `4002`
- XXL-Job Admin: `7070`

### 可观测性
- OTel Collector: `4317`(gRPC) / `4318`(HTTP) / `8889`(Prometheus)
- Prometheus: `9090`
- Loki: `3100`
- Tempo: `3200` / `9095`(内部)
- Grafana: `3000`
- Alertmanager: `9093`

## Docker Compose 文件映射

| 文件 | 服务 |
|------|------|
| `docker-compose.core.yaml` | MySQL, Redis, Nacos |
| `docker-compose.storage.yaml` | MinIO |
| `docker-compose.jobs.yaml` | RocketMQ, XXL-Job |
| `docker-compose.observability.yaml` | OTel, Prometheus, Loki, Tempo, Grafana, Alertmanager |

## 端口范围规划

| 范围 | 用途 |
|------|------|
| 3000-3999 | 可视化服务 |
| 4000-4999 | 注册中心与遥测 |
| 6000-6999 | 业务微服务 |
| 7000-7999 | 任务调度 |
| 8000-8999 | API 与内部通信 |
| 9000-9999 | 监控告警 |
| 10000-19999 | 中间件 |
