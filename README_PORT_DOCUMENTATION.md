# Papertrace Port 接口文档指南

## 快速开始

本项目包含关于所有 Port 接口的完整文档。根据您的需要选择合适的文档：

### 选择您的文档

| 我想... | 打开这个文档 |
|--------|-----------|
| 快速找到一个接口及其方法 | [`PORT_QUICK_REFERENCE.md`](PORT_QUICK_REFERENCE.md) |
| 理解每个接口的详细设计 | [`PORT_INTERFACES_SUMMARY.md`](PORT_INTERFACES_SUMMARY.md) |
| 理解接口如何相互调用 | [`PORT_DEPENDENCY_ANALYSIS.md`](PORT_DEPENDENCY_ANALYSIS.md) |
| 快速导航找到合适文档 | [`PORT_INTERFACES_INDEX.md`](PORT_INTERFACES_INDEX.md) |

---

## 10 个 Port 接口一览

### patra-ingest-domain (7 个)
1. **PatraRegistryPort** - 获取配置快照
2. **StorageMetadataPort** - 记录上传元数据
3. **LiteratureStoragePort** - 存储文献
4. **ExpressionCompilerPort** - 编译表达式
5. **OutboxPublisherPort** - 发布消息
6. **PubmedSearchPort** - 查询规划元数据
7. **TechnicalRetryPort** - 失败重试处理

### patra-registry-domain (2 个)
1. **ProvenanceConfigRepository** - 配置查询（9 个方法）
2. **ExprRepository** - 表达式快照

### patra-storage-domain (1 个)
1. **FileMetadataRepository** - 文件元数据

---

## 文档结构

```
PORT_QUICK_REFERENCE.md
├─ 3 个模块的接口表
├─ 调用关系图
└─ 最常用的接口示例

PORT_INTERFACES_SUMMARY.md
├─ 7 个 patra-ingest-domain Port 详细说明
├─ 2 个 patra-registry-domain Port 详细说明
└─ 1 个 patra-storage-domain Port 说明

PORT_DEPENDENCY_ANALYSIS.md
├─ 分层架构图
├─ 依赖关系矩阵
├─ 调用流程示例
└─ 设计特性说明

PORT_INTERFACES_INDEX.md
├─ 文档导航指南
├─ 快速查询方式
└─ 常见问题解答
```

---

## 关键概念

### 什么是 Port?

Port 是 Hexagonal Architecture 中的核心概念：
- **定义**: Domain 层中的 Java 接口（纯技术无关）
- **实现**: Infrastructure 层中的具体实现（Feign、MyBatis、S3 等）
- **使用**: Application 层的应用服务调用这些接口
- **目的**: 依赖倒置，让领域层独立于具体技术

### Port 类型

- **Query Port** - 读取数据（Repository 接口）
- **Command Port** - 执行操作（Service 接口）

---

## 使用建议

### 快速查找接口方法签名
→ 打开 `PORT_QUICK_REFERENCE.md`，查看按模块分类的表格

### 理解接口的完整设计
→ 打开 `PORT_INTERFACES_SUMMARY.md`，查看完整的接口定义和 Javadoc

### 理解接口之间如何调用
→ 打开 `PORT_DEPENDENCY_ANALYSIS.md`，查看调用流程和依赖关系

### 找不到正确的文档
→ 打开 `PORT_INTERFACES_INDEX.md`，按照指南快速导航

---

## 文件源位置

### patra-ingest-domain
```
patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/
├── PatraRegistryPort.java
├── StorageMetadataPort.java
├── LiteratureStoragePort.java
├── ExpressionCompilerPort.java
├── OutboxPublisherPort.java
├── PubmedSearchPort.java
└── TechnicalRetryPort.java
```

### patra-registry-domain
```
patra-registry/patra-registry-domain/src/main/java/com/patra/registry/domain/port/
├── ProvenanceConfigRepository.java
└── ExprRepository.java
```

### patra-storage-domain
```
patra-storage/patra-storage-domain/src/main/java/com/patra/storage/domain/port/
└── FileMetadataRepository.java
```

---

## 常见问题

**Q: 我需要在应用服务中使用一个 Port，怎么做?**

A: 在应用服务的构造函数中注入 Port 接口：
```java
@Service
public class MyApplicationService {
    private final PatraRegistryPort registryPort;
    
    public MyApplicationService(PatraRegistryPort registryPort) {
        this.registryPort = registryPort;
    }
}
```

**Q: Port 的实现在哪里?**

A: 在相应模块的 `*-infra` 或 `*-adapter` 包中，通常命名为 `*PortImpl`。

**Q: 如何添加新的 Port?**

A: 
1. 在 `*-domain` 模块的 `port` 包定义接口
2. 在 `*-infra` 或 `*-adapter` 实现
3. 在 Spring 配置中注册为 Bean

**Q: Port 中的 Record 类型是什么?**

A: Java 14+ 的不可变数据结构，用于 Request/Result/Context 等数据传输对象。

---

## 下一步

1. 打开 `PORT_INTERFACES_INDEX.md` 进行快速导航
2. 根据您的需要打开相应的详细文档
3. 查看源代码文件以了解实现细节

---

**文档更新**: 2025年11月2日

**维护**: 定期审查并更新以反映代码变化

