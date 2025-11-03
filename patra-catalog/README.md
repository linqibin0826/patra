# patra-catalog

## 概述

**patra-catalog** 是 Papertrace 医学文献数据平台的文献目录服务,负责文献数据的存储、管理和查询。作为核心业务服务,它接收来自 patra-ingest 的采集数据,并为其他服务提供文献信息查询能力。

**当前状态**: 🚧 **规划中** - 目前仅实现了 API 契约层(patra-catalog-api),核心业务逻辑和数据持久化层尚在开发中。

## 核心职责

- **文献数据存储**: 持久化采集的文献元数据(标题、摘要、作者、期刊等)
- **数据管理**: 提供文献信息的增删改查操作
- **跨服务契约**: 定义文献数据传输的标准化 DTO
- **查询服务**: 为其他微服务提供文献信息检索接口(规划中)

## 模块结构

```
patra-catalog/
├── patra-catalog-api/              # API 契约层(已实现)
│   ├── src/main/java/com/patra/catalog/api/dto/
│   │   ├── LiteratureDTO.java     # 文献数据传输对象
│   │   ├── JournalDTO.java        # 期刊数据传输对象
│   │   └── AuthorDTO.java         # 作者数据传输对象
│   └── pom.xml
├── patra-catalog-domain/           # 领域层(待实现)
├── patra-catalog-app/              # 应用层(待实现)
├── patra-catalog-infra/            # 基础设施层(待实现)
├── patra-catalog-adapter/          # 适配器层(待实现)
├── patra-catalog-boot/             # 启动模块(待实现)
└── pom.xml
```

## 主要组件

### patra-catalog-api (已实现)

API 模块定义了跨服务通信的数据契约,遵循 DDD 的"发布语言"(Published Language)模式。

#### LiteratureDTO
文献数据传输对象,包含完整的文献元数据:

```java
@Builder
public record LiteratureDTO(
    @NotBlank String title,              // 文献标题
    String abstractText,                 // 摘要
    List<AuthorDTO> authors,             // 作者列表
    JournalDTO journal,                  // 期刊信息
    Map<String, String> identifiers,     // 外部标识符(PMID, DOI, PMC)
    LocalDate publicationDate,           // 发表日期
    List<String> keywords,               // 关键词/MeSH 术语
    String language,                     // 语言代码(ISO 639-1)
    List<String> publicationTypes        // 出版类型
)
```

**设计特点**:
- 使用 Java Record 确保不可变性
- 集成 `@Builder` 模式便于构造
- 包含 Jakarta Validation 约束保证数据完整性
- 支持向后兼容(新字段使用默认值)

#### JournalDTO
期刊数据传输对象:

```java
@Builder
public record JournalDTO(
    String title,      // 期刊名称
    String issn,       // 国际标准期刊号
    String issnType,   // ISSN 类型(印刷版/电子版/链接版)
    String publisher,  // 出版商
    String country     // 出版国家/地区
)
```

#### AuthorDTO
作者数据传输对象:

```java
@Builder
public record AuthorDTO(
    String lastName,           // 姓
    String foreName,           // 名
    String initials,           // 姓名缩写
    List<String> affiliations, // 机构隶属关系
    String identifier,         // 作者唯一标识符(如 ORCID)
    String identifierSource    // 标识符来源系统
)
```

## 规划架构

patra-catalog 将采用**六边形架构 + DDD** 模式,包含以下层次:

### Domain 层(领域层)
- 纯 Java 实现,无框架依赖
- 定义文献、作者、期刊等核心领域实体
- 实现业务规则和领域逻辑

### Application 层(应用层)
- 编排领域服务完成业务用例
- 事务管理和服务编排

### Infrastructure 层(基础设施层)
- 数据持久化(MyBatis-Plus + MySQL)
- 仓储实现(Repository)
- 外部服务集成

### Adapter 层(适配器层)
- REST 控制器(提供 HTTP API)
- 事件监听器(接收 patra-ingest 的文献采集事件)

### Boot 层(启动模块)
- Spring Boot 启动类
- 依赖装配和配置

## 技术栈

| 组件 | 版本/说明 |
|------|----------|
| **Spring Boot** | 3.5.7 |
| **patra-common-core** | Papertrace 公共核心库 |
| **Jakarta Validation** | DTO 数据校验 |
| **Lombok** | Builder 模式支持 |
| **MyBatis-Plus** | 数据持久化(规划中) |
| **MySQL** | 数据存储(规划中) |

## 与其他服务的集成

### patra-ingest → patra-catalog
- **数据流向**: patra-ingest 采集文献数据后,通过 `LiteratureDTO` 传递给 patra-catalog
- **集成方式**: REST API 或事件驱动(待定)

### patra-catalog → 其他服务
- **查询接口**: 其他服务可通过 REST API 查询文献信息(规划中)

## 开发路线图

- [x] 定义 API 契约层(LiteratureDTO, JournalDTO, AuthorDTO)
- [ ] 实现 Domain 层(文献、作者、期刊实体和领域服务)
- [ ] 实现 Infrastructure 层(数据库表设计和 MyBatis 仓储)
- [ ] 实现 Application 层(文献管理用例)
- [ ] 实现 Adapter 层(REST API 和事件监听器)
- [ ] 实现 Boot 启动模块并集成到网关路由
- [ ] 编写单元测试和集成测试

---

**当前状态**: 🚧 规划中,仅 API 层已实现
**最后更新**: 2025-01-12
**版本**: 0.1.0-SNAPSHOT
