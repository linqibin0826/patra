---
type: adr
adr_id: 6
date: 2025-12-01
status: accepted
date_decided: 2025-12-01
deciders: [Qibin Lin]
technical_debt: none
tags:
  - decision/architecture
  - tech/caching
  - domain/mesh
---

# ADR-006: MeSH 源文件缓存策略

## 状态

**accepted**

## 背景

MeSH（Medical Subject Headings）数据导入需要从 NLM（美国国家医学图书馆）官方服务器下载 XML 源文件：
- Descriptor XML：约 300MB，包含 ~35,000 条主题词
- Qualifier XML：约 1MB，包含 ~80 条限定词

当前存在的问题：
1. **下载速度慢**：NLM 服务器位于美国，国内下载速度不稳定（100KB/s ~ 1MB/s）
2. **网络不可靠**：长时间下载过程中可能因网络波动导致失败
3. **重复下载**：每次重新导入（调试、重跑）都需要重新下载完整文件
4. **资源浪费**：年度 MeSH 数据变化不频繁，但每次导入都从远程下载

## 决策

我们将引入对象存储缓存机制，采用**缓存优先 + 静默降级**策略：

1. **缓存优先**：检查 MinIO 对象存储中是否存在缓存文件，存在则直接下载
2. **异步上传**：远程下载完成后，异步上传到对象存储作为缓存（不阻塞主流程）
3. **静默降级**：缓存检查或下载失败时，自动回退到远程下载（记录 warn 日志）
4. **条件装配**：根据 `ObjectStorageOperations` Bean 的存在性自动选择实现

缓存键格式：`{keyPrefix}/{dataType}s/{filePrefix}{version}.xml`
- 示例：`mesh/descriptors/desc2025.xml`、`mesh/qualifiers/qual2025.xml`

## 后果

### 正面影响

- **导入性能提升**：第二次及后续导入从 MinIO 读取，速度从分钟级降到秒级
- **网络稳定性**：MinIO 部署在内网，消除了跨境网络不稳定因素
- **成本节约**：减少对 NLM 服务器的请求，降低带宽消耗
- **开发体验**：调试和重跑导入时无需等待远程下载
- **渐进式采用**：通过条件装配，无对象存储环境可正常工作

### 负面影响

- **存储成本**：需要额外的 MinIO 存储空间（约 600MB/年）
- **缓存失效**：需要手动清理过期缓存（年度更新时）
- **配置复杂度**：需要配置 MinIO 连接和异步线程池

### 风险

- 缓存文件损坏时可能导致导入失败（静默降级机制缓解）
- 缓存清理不及时可能导致使用旧版本数据

## 替代方案

### 方案 A：本地文件系统缓存

使用本地文件系统存储缓存文件。

**优点**：
- 无需额外基础设施
- 实现简单，无网络开销

**缺点**：
- 不支持多实例部署（每个实例独立缓存）
- 磁盘空间管理复杂
- 无法在 Kubernetes 环境中共享

### 方案 B：Redis 缓存

使用 Redis 存储缓存文件内容。

**优点**：
- 支持多实例共享
- 支持自动过期

**缺点**：
- 大文件（300MB）不适合存储在 Redis
- 内存成本高
- 需要额外的 Redis 配置

### 方案 C：CDN 加速

使用 CDN 加速 NLM 源文件下载。

**优点**：
- 无需本地存储
- 全球加速

**缺点**：
- 需要 CDN 服务订阅
- NLM 源文件不支持自定义 CDN 回源
- 无法控制缓存策略

## 参考资料

- [NLM MeSH Download](https://www.nlm.nih.gov/databases/download/mesh.html)
- [MinIO Object Storage](https://min.io/)
