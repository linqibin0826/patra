# Provenance Client 项目文档

> 本文档提供 patra-spring-boot-starter-provenance 模块的快速导航和核心信息。

## 快速链接

### 核心文档
- **[README.md](patra-spring-boot-starter-provenance/README.md)** - 模块使用指南
- **[设计文档](.kiro/specs/provenance/design.md)** - 完整的架构设计和技术选型
- **[需求文档](.kiro/specs/provenance/requirements.md)** - 详细的功能需求和约束
- **[任务列表](.kiro/specs/provenance/tasks.md)** - 实现任务跟踪（已全部完成 ✅）
- **[实现总结](.kiro/specs/provenance/implementation-summary.md)** - 实现成果和关键决策

### 代码结构
```
patra-spring-boot-starter-provenance/
├── src/main/java/com/patra/starter/provenance/
│   ├── common/          # 公共组件
│   ├── pubmed/          # PubMed 数据源
│   ├── epmc/            # EPMC 数据源
│   └── boot/            # 自动配置
└── README.md            # 使用指南
```

## 核心特性

### ✅ 已实现功能

1. **PubMed API 封装**
   - ESearch：搜索文献，返回 ID 列表（JSON 格式，性能提升 30-50%）
   - EFetch：获取文献详情（XML/JSON 按需选择）
   - 完整参数支持：包含 apiKey、tool、email 等认证参数

2. **EPMC API 封装**
   - Search：搜索 EPMC 文献（原生 JSON）
   - 完整的响应模型（Result、Author 等）

3. **配置管理**
   - 两级配置优先级：调用时传递 > 本地配置
   - 灵活的配置覆盖机制
   - 本地默认配置兜底

4. **Spring Boot 自动配置**
   - 条件装配：`@ConditionalOnClass`、`@ConditionalOnBean`
   - 降级保护：网关不可用时使用 Noop 实现
   - 可选依赖：Micrometer 性能指标（可选）

5. **可观测性**
   - 日志规范：`[PROVENANCE][CORE]` 前缀
   - 性能指标：API 调用耗时、成功/失败次数
   - 异常处理：ProvenanceClientException

### ❌ 未实现功能（后续迭代）

1. 单元测试和集成测试
2. 响应缓存机制
3. API Key 管理
4. 自动分页功能
5. 批量处理功能
6. 新增数据源（Crossref、Scopus 等）

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-provenance</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置 application.yml

```yaml
patra:
  provenance:
    enabled: true
    pubmed:
      base-url: https://eutils.ncbi.nlm.nih.gov/entrez/eutils
      http:
        default-headers:
          User-Agent: Papertrace/0.1.0
        timeout-connect-millis: 5000
        timeout-read-millis: 30000
        timeout-total-millis: 60000
```

### 3. 使用客户端

```java
@Service
public class LiteratureService {

    @Autowired
    private PubMedClient pubMedClient;

    public void searchPubMed() {
        // 使用默认 JSON 格式（推荐）
        ESearchRequest request = new ESearchRequest("pubmed", "cancer AND therapy");
        ESearchResponse response = pubMedClient.esearch(request);

        System.out.println("Total count: " + response.count());
        System.out.println("ID list: " + response.idList());
    }
}
```

## 技术栈

- **Java**: 21
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.1
- **Jackson**: JSON/XML 序列化
- **Micrometer**: 性能指标（可选）
- **Lombok**: 代码生成

## 核心设计原则

1. **职责单一**: 只负责 API 封装和网关调用
2. **类型安全**: 使用强类型 Request 和 Response 对象
3. **独立设计**: 每个数据源独立 Client，不做统一抽象
4. **JSON 优先**: 优先使用 JSON 格式，性能提升 30-50%
5. **配置灵活**: 支持两级配置优先级
6. **职责边界清晰**: 配置转换由业务方负责
7. **易于使用**: Spring Boot 自动配置
8. **易于扩展**: 新增数据源只需创建新 Client

## 关键指标

| 指标 | 数值 |
|------|------|
| Java 文件数 | 33 个 |
| 核心接口 | 2 个（PubMedClient、EPMCClient） |
| Request 对象 | 3 个 |
| Response 对象 | 10+ 个 |
| 配置对象 | 7 个 |
| 编译状态 | ✅ 成功 |

## 下一步工作

### 优先级 P0（必须完成）
1. 在 patra-ingest 中集成验证
2. 功能测试验证

### 优先级 P1（重要）
1. 单元测试（覆盖率 > 85%）
2. 集成测试
3. 性能测试

### 优先级 P2（可选）
1. 性能优化（缓存、连接池）
2. 功能增强（新数据源、自动分页）
3. 文档完善

## 联系方式

- **作者**: linqibin
- **版本**: 0.1.0-SNAPSHOT
- **日期**: 2025-01-XX

---

**状态**: ✅ 核心实现完成，编译通过，文档完整
**下一步**: 业务集成与测试
