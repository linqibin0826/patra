---
type: adr
adr_id: 9
date: 2025-12-01
status: accepted
date_decided: 2025-12-01
deciders: [Qibin Lin]
technical_debt: none
tags:
  - decision/architecture
  - tech/rest-client
  - tech/observability
---

# ADR-009: REST 客户端下载进度监控

## 状态

**accepted**

## 背景

Patra 平台需要从外部数据源（PubMed、EPMC、NLM 等）下载大文件：
- **MeSH 描述符文件**：约 300MB，XML 格式
- **期刊列表文件**：约 50MB，CSV/XML 格式
- **文献全文文件**：大小不等，可能达到数 GB

当前存在的问题：
1. **无进度反馈**：大文件下载时用户无法知道进度，体验差
2. **缺乏监控**：无法追踪下载成功率、耗时分布、失败原因
3. **超时配置不当**：默认 RestClient 超时不适合大文件下载
4. **错误处理分散**：各处下载代码的错误处理方式不一致

## 决策

我们将在 `patra-spring-boot-starter-rest-client` 中引入下载进度监控机制：

### 核心组件

1. **DownloadClient**：专用下载客户端接口，支持进度回调
2. **ProgressListener**：观察者模式的进度监听器接口
3. **DownloadProgress**：进度数据对象，包含字节数、速度、剩余时间等

### 设计决策

#### 1. 使用 RestClient 而非 WebClient

**选择 RestClient 的原因**：
- 与项目现有技术栈一致（Spring Boot 3.x 推荐的同步 HTTP 客户端）
- 大文件下载是 I/O 密集型操作，同步模型足够
- 代码更简单，易于调试和维护
- 避免响应式编程的复杂性（背压处理、错误传播等）

#### 2. 进度节流策略（500ms 间隔）

**选择 500ms 的原因**：
- 平衡实时性和性能开销
- 避免高频回调影响下载吞吐量
- 人类感知的最小更新间隔约 100-200ms，500ms 足够流畅
- 减少日志量和指标采集压力

#### 3. 64KB 缓冲区

**选择 64KB 的原因**：
- 操作系统常见的页大小倍数（4KB × 16）
- 在大多数网络环境下性能最优
- 避免过小缓冲区导致频繁系统调用
- 避免过大缓冲区占用过多内存

#### 4. 组合模式的监听器（CompositeProgressListener）

**设计要点**：
- 支持同时注册多个监听器（日志 + 指标）
- 单个监听器异常不影响其他监听器
- 工厂方法 `of()` 简化创建逻辑

### 可观测性集成

记录以下 Micrometer 指标：

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `download.duration.seconds` | Timer | 下载耗时分布 |
| `download.bytes.total` | Counter | 累计下载字节数 |
| `download.count.total` | Counter | 累计下载次数（按状态分组） |
| `download.failure.total` | Counter | 失败次数（按错误类型分组） |

**设计决策**：不记录瞬时进度（Gauge），原因：
- 多个并发下载会导致指标覆盖
- 瞬时进度是任务状态，不适合 Prometheus 的拉取模型
- 避免 cardinality 爆炸

### 配置

使用 `longRunningRestClient`（10 分钟读取超时），适配大文件下载场景：

```yaml
patra:
  rest-client:
    clients:
      long-running:
        timeout:
          connect: 30s
          read: 10m
```

## 后果

### 正面影响

- **用户体验提升**：下载进度实时反馈，大文件下载不再"黑盒"
- **监控完善**：Grafana 可视化下载成功率、耗时分布、流量统计
- **错误处理统一**：`DownloadException` 携带 ErrorTrait，上层可基于语义处理
- **复用便捷**：各服务注入 `DownloadClient` 即可使用

### 负面影响

- **依赖增加**：使用下载功能的模块需要依赖 `patra-spring-boot-starter-rest-client`
- **复杂度增加**：引入了监听器模式，学习成本略有增加

### 风险

- 进度回调中的异常可能影响下载（已通过 CompositeProgressListener 异常隔离解决）
- ThreadLocal 在线程池复用场景可能泄漏（已在 onComplete/onError 中自动清理）

## 替代方案

### 方案 A：使用 WebClient 响应式下载

```java
webClient.get()
    .uri(url)
    .retrieve()
    .bodyToFlux(DataBuffer.class)
    .map(buffer -> { /* 处理进度 */ })
```

**优点**：
- 非阻塞，适合高并发场景
- 背压自动处理

**缺点**：
- 响应式编程复杂度高
- 与现有同步代码风格不一致
- 错误处理和调试困难
- 团队学习成本高

### 方案 B：使用 Apache HttpClient

```java
HttpClient httpClient = HttpClients.createDefault();
HttpGet request = new HttpGet(url);
CloseableHttpResponse response = httpClient.execute(request);
```

**优点**：
- 成熟稳定，功能丰富
- 完全控制底层行为

**缺点**：
- 需要额外依赖
- 与 Spring 生态集成不如 RestClient 顺畅
- 配置繁琐

### 方案 C：使用 Java NIO 异步下载

```java
AsynchronousFileChannel.open(path, StandardOpenOption.WRITE)
    .write(buffer, position, attachment, handler);
```

**优点**：
- JDK 原生支持
- 异步非阻塞

**缺点**：
- 代码复杂度极高
- 需要手动管理 HTTP 协议
- 错误处理困难

## 参考资料

- [Spring RestClient Documentation](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [Micrometer Timer/Counter](https://micrometer.io/docs/concepts#_meters)
- [[ADR-005-adopt-opentelemetry-grafana-stack-for-observability|ADR-005: 采用 OpenTelemetry + Grafana 可观测性方案]]
