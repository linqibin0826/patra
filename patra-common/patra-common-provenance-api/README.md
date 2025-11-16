# patra-common-provenance-api — Provenance API 常量与枚举

> **Provenance 数据源 API 常量的单一事实来源（SSOT）**,提供类型安全的枚举、端点路径和参数键名。

---

## 概述

`patra-common-provenance-api` 是 Patra 平台的 Provenance API 常量模块,集中管理所有数据源（PubMed、EPMC、Crossref 等）的 API 常量,包括端点路径、参数键名和参数值枚举。本模块作为跨模块共享的单一事实来源,确保 API 常量的一致性和类型安全。

**设计目标**:
- **类型安全**: 使用枚举替代魔法字符串,提供编译时检查
- **IDE 友好**: 自动补全、重构安全、快速导航
- **单一事实来源**: 所有 Provenance API 常量集中管理
- **易于扩展**: 新增数据源只需添加对应的常量类和枚举

---

## 核心职责

- **API 端点管理**: 定义数据源的 HTTP 端点路径（如 `/esearch.fcgi`）
- **参数键管理**: 定义 HTTP 查询参数名称（如 `"retmode"`）
- **参数值枚举**: 提供类型安全的参数值枚举（如 `RetMode.JSON`）
- **跨模块共享**: 支持 starter-provenance、测试、监控、CLI 等模块使用

---

## 模块结构

```
patra-common-provenance-api/
├── endpoints/                       (API 端点路径常量)
│   ├── PubMedEndpoints             (PubMed 端点: esearch/efetch/epost)
│   ├── EpmcEndpoints               (EPMC 端点: search)
│   └── CrossrefEndpoints           (Crossref 端点: works)
│
├── params/                          (参数键名常量)
│   ├── PubMedParamKeys             (PubMed 参数键)
│   ├── EpmcParamKeys               (EPMC 参数键)
│   └── CrossrefParamKeys           (Crossref 参数键)
│
└── values/                          (参数值枚举 - 类型安全)
    ├── pubmed/
    │   ├── RetMode                 (返回格式: JSON/XML/TEXT)
    │   ├── RetType                 (返回类型: COUNT/UILIST/ABSTRACT)
    │   ├── UseHistory              (历史服务器: YES/NO)
    │   └── DateType                (日期类型: PUBLICATION_DATE/ENTREZ_DATE/...)
    └── epmc/
        ├── Format                  (格式: JSON/XML/LITE/CORE)
        └── ResultType              (结果类型: LITE/CORE/IDLIST)
```

---

## 主要组件

### 1. endpoints — API 端点路径常量

**设计定位**: HTTP API 端点的单一事实来源

#### PubMedEndpoints
```java
public static final String ESEARCH = "/esearch.fcgi";  // 搜索端点
public static final String EFETCH = "/efetch.fcgi";    // 抓取端点
public static final String EPOST = "/epost.fcgi";      // 发布端点
```

#### EpmcEndpoints
```java
public static final String SEARCH = "/search";  // 搜索端点
```

#### CrossrefEndpoints
```java
public static final String WORKS = "/works";  // Works 查询端点
```

---

### 2. params — 参数键名常量

**设计定位**: HTTP 查询参数名称的单一事实来源

#### PubMedParamKeys
```java
// 分页参数
public static final String RETSTART = "retstart";
public static final String RETMAX = "retmax";

// 格式参数
public static final String RETMODE = "retmode";
public static final String RETTYPE = "rettype";

// 查询参数
public static final String TERM = "term";
public static final String DATETYPE = "datetype";
public static final String MINDATE = "mindate";
public static final String MAXDATE = "maxdate";

// 历史参数
public static final String USEHISTORY = "usehistory";
public static final String WEBENV = "WebEnv";  // 注意驼峰命名
public static final String QUERY_KEY = "query_key";

// 身份参数
public static final String API_KEY = "api_key";
public static final String TOOL = "tool";
public static final String EMAIL = "email";
```

---

### 3. values — 参数值枚举（类型安全）⭐

**设计定位**: 替代魔法字符串,提供编译时类型检查

#### RetMode（PubMed 返回格式）
```java
public enum RetMode {
    JSON("json"),    // JSON 格式（推荐）
    XML("xml"),      // XML 格式
    TEXT("text");    // 纯文本格式

    public String value();                                    // 获取 API 参数值
    public static RetMode fromString(String value);           // 从字符串解析
    public static RetMode fromStringOrDefault(...);           // 安全解析
}
```

#### RetType（PubMed 返回类型）
```java
public enum RetType {
    COUNT("count"),        // 仅返回数量
    UILIST("uilist"),      // 返回 ID 列表
    ABSTRACT("abstract");  // 返回摘要
}
```

#### UseHistory（PubMed 历史服务器）
```java
public enum UseHistory {
    YES("y"),  // 使用历史服务器（推荐用于批量数据）
    NO("n");   // 不使用历史服务器

    public static UseHistory fromBoolean(boolean useHistory);  // 布尔值转换
    public boolean toBoolean();                                // 转为布尔值
}
```

#### DateType（PubMed 日期类型）
```java
public enum DateType {
    PUBLICATION_DATE("pdat"),    // 发表日期
    ENTREZ_DATE("edat"),         // 录入日期
    MODIFICATION_DATE("mdat");   // 修改日期
}
```

#### Format（EPMC 格式）
```java
public enum Format {
    JSON("json"),  // JSON 格式
    XML("xml"),    // XML 格式
    LITE("lite"),  // 轻量级
    CORE("core");  // 核心字段
}
```

---

## 依赖关系

**上游依赖**:
- `jackson-annotations`: 枚举序列化支持（`@JsonValue`）

**下游消费者**:
- **patra-spring-boot-starter-provenance**: 主要使用者
- **patra-ingest**: 数据采集服务
- **测试模块**: 集成测试、E2E 测试
- **监控模块**: 健康检查、端点验证
- **CLI 工具**: 命令行管理工具

---

## 使用示例

### Maven 依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-common-provenance-api</artifactId>
</dependency>
```

### 示例 1: 使用端点常量

```java
import com.patra.common.provenance.api.endpoints.PubMedEndpoints;

// ❌ Before: 魔法字符串
String url = baseUrl + "/esearch.fcgi";

// ✅ After: 类型安全常量
String url = baseUrl + PubMedEndpoints.ESEARCH;
```

### 示例 2: 使用参数键

```java
import com.patra.common.provenance.api.params.PubMedParamKeys;

// ❌ Before: 魔法字符串
params.put("retmode", "json");
params.put("term", "cancer");

// ✅ After: 使用常量
params.put(PubMedParamKeys.RETMODE, "json");
params.put(PubMedParamKeys.TERM, "cancer");
```

### 示例 3: 使用枚举（类型安全）⭐

```java
import com.patra.common.provenance.api.values.pubmed.*;

// ❌ Before: 魔法字符串
params.put("retmode", "json");  // 可能拼写错误
if (retmode.equalsIgnoreCase("xml")) { ... }

// ✅ After: 类型安全枚举
params.put("retmode", RetMode.JSON.value());  // 编译时检查
if (request.retmode() == RetMode.XML) { ... }  // null-safe
```

### 示例 4: 构建 PubMed 请求（完整示例）

```java
import com.patra.common.provenance.api.endpoints.PubMedEndpoints;
import com.patra.common.provenance.api.params.PubMedParamKeys;
import com.patra.common.provenance.api.values.pubmed.*;

// 构建查询参数
Map<String, String> params = new HashMap<>();
params.put(PubMedParamKeys.TERM, "cancer treatment");
params.put(PubMedParamKeys.RETMODE, RetMode.JSON.value());
params.put(PubMedParamKeys.RETTYPE, RetType.COUNT.value());
params.put(PubMedParamKeys.DATETYPE, DateType.PUBLICATION_DATE.value());
params.put(PubMedParamKeys.MINDATE, "2023/01/01");
params.put(PubMedParamKeys.MAXDATE, "2023/12/31");
params.put(PubMedParamKeys.USEHISTORY, UseHistory.YES.value());

// 构建完整 URL
String url = baseUrl + PubMedEndpoints.ESEARCH;

// 发起请求
restClient.get()
    .uri(uriBuilder -> {
        uriBuilder.path(PubMedEndpoints.ESEARCH);
        params.forEach(uriBuilder::queryParam);
        return uriBuilder.build();
    })
    .retrieve()
    .body(String.class);
```

### 示例 5: 从字符串解析枚举

```java
// 从配置或外部输入解析
String modeStr = config.get("retmode");
RetMode mode = RetMode.fromStringOrDefault(modeStr, RetMode.JSON);

// 从布尔值转换
boolean useHistory = true;
UseHistory history = UseHistory.fromBoolean(useHistory);
```

### 示例 6: 枚举的类型安全比较

```java
// ❌ Before: 字符串比较
if (retmode != null && retmode.equalsIgnoreCase("xml")) {
    processXml(response);
}

// ✅ After: 枚举比较
if (request.retmode() == RetMode.XML) {
    processXml(response);
}
```

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| **Java** | 25 | 使用现代枚举特性 |
| **Jackson** | (继承自 parent) | 枚举序列化支持 |

---

## 设计考量

### 1. 为什么使用枚举而非常量？

**❌ 使用字符串常量的问题**:
```java
// 问题 1: 无编译时检查
params.put("retmode", "jsoon");  // 拼写错误,编译通过,运行时才发现

// 问题 2: 不知道有哪些可选值
params.put("retmode", "???");  // IDE 无法提示

// 问题 3: 字符串比较容易出错
if (mode.equals("json")) { ... }  // null-unsafe
```

**✅ 使用枚举的优势**:
```java
// 优势 1: 编译时类型检查
params.put("retmode", RetMode.JSON.value());  // 拼写错误编译失败

// 优势 2: IDE 自动补全
RetMode mode = RetMode.  // IDE 提示: JSON, XML, TEXT

// 优势 3: 类型安全比较
if (mode == RetMode.JSON) { ... }  // null-safe, 性能好

// 优势 4: 重构安全
// 重命名枚举值时,IDE 自动更新所有引用
```

### 2. 枚举设计模式

所有枚举遵循统一的设计模式:

- ✅ **`value()` 方法**: 返回 API 参数字符串值
- ✅ **`@JsonValue` 注解**: Jackson 序列化支持
- ✅ **`fromString()` 方法**: 从字符串解析（严格模式,失败抛异常）
- ✅ **`fromStringOrDefault()` 方法**: 安全解析（失败返回默认值）
- ✅ **`toString()` 重写**: 返回可读值

### 3. 参数键为何不用枚举？

**参数键保持字符串常量**,理由:
- 参数键是 HTTP 协议的一部分,通常直接使用字符串
- 枚举会增加一层转换,但收益不大
- 与主流框架（Spring、JAX-RS）的使用习惯一致

### 4. 端点路径为何不用枚举？

**端点路径保持字符串常量**,理由:
- 路径是固定字符串,没有"类型"的语义
- 用常量更简洁直观
- 方便字符串拼接和 URL 构建

---

## 扩展指南

### 添加新数据源

#### 步骤 1: 创建端点常量
```java
// endpoints/NewSourceEndpoints.java
public final class NewSourceEndpoints {
    public static final String SEARCH = "/api/search";
}
```

#### 步骤 2: 创建参数键常量
```java
// params/NewSourceParamKeys.java
public final class NewSourceParamKeys {
    public static final String QUERY = "q";
    public static final String LIMIT = "limit";
}
```

#### 步骤 3: 创建参数值枚举
```java
// values/newsource/Format.java
public enum Format {
    JSON("json"),
    XML("xml");

    // 遵循统一模式
}
```

### 添加新参数值枚举

```java
package com.patra.common.provenance.api.values.pubmed;

public enum SortOrder {
    RELEVANCE("relevance"),
    PUB_DATE("pub_date"),
    AUTHOR("author");

    private final String value;

    // 遵循统一模式: value(), fromString(), fromStringOrDefault()
}
```

---

## 架构优势

### 1. 单一事实来源（SSOT）
- 所有 Provenance API 常量集中在一个地方
- 避免重复定义和不一致
- 变更时只需修改一处

### 2. 类型安全
- 编译时检查,避免拼写错误
- IDE 自动补全,提升开发效率
- 重构安全,减少维护成本

### 3. 易于测试
- Mock 端点时使用常量,测试更可靠
- 枚举值的范围明确,易于覆盖测试

### 4. 跨模块共享
- starter-provenance 使用
- 测试模块验证端点
- 监控模块健康检查
- CLI 工具管理配置

### 5. 版本隔离
- API 变更只影响 Provenance 相关模块
- 不污染通用基础设施模块（patra-common-core）

---

## 迁移指南

### 从 starter-provenance 迁移

**原代码**（starter-provenance 内部）:
```java
import com.patra.starter.provenance.pubmed.request.PubMedParamKeys;

String path = "/esearch.fcgi";
params.put(PubMedParamKeys.RETMODE, "json");
```

**新代码**（使用 patra-common-provenance-api）:
```java
import com.patra.common.provenance.api.endpoints.PubMedEndpoints;
import com.patra.common.provenance.api.params.PubMedParamKeys;
import com.patra.common.provenance.api.values.pubmed.RetMode;

String path = PubMedEndpoints.ESEARCH;
params.put(PubMedParamKeys.RETMODE, RetMode.JSON.value());
```

---

## 相关文档

- [patra-common/README.md](../README.md) — 多模块聚合器总览
- [patra-common-core/README.md](../patra-common-core/README.md) — 核心基础设施
- [patra-common-model/README.md](../patra-common-model/README.md) — 共享数据模型
- [patra-spring-boot-starter-provenance/README.md](../../patra-spring-boot-starter-provenance/README.md) — Provenance Starter

---

**Maven 坐标**: `com.patra:patra-common-provenance-api`
**版本**: 0.1.0-SNAPSHOT
**最后更新**: 2025-11-16
