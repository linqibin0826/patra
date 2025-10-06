# Provenance Client 项目文档

> 本文档提供 patra-spring-boot-starter-provenance 模块的快速导航和核心信息，便于研发、测试与运维协同。

## 快速链接

### 核心文档
- **[README.md](patra-spring-boot-starter-provenance/README.md)** – 使用指南与配置说明
- **[设计文档](.kiro/specs/provenance/design.md)** – 完整架构设计与技术选型
- **[需求文档](.kiro/specs/provenance/requirements.md)** – 功能需求与验收标准
- **[任务列表](.kiro/specs/provenance/tasks.md)** – 任务拆解与完成记录
- **[实现总结](.kiro/specs/provenance/implementation-summary.md)** – 历史决策与经验沉淀

### 代码结构
```
patra-spring-boot-starter-provenance/
├── src/main/java/com/patra/starter/provenance/
│   ├── common/          # 公共组件（配置、指标、网关工具）
│   ├── pubmed/          # PubMed 数据源封装
│   ├── epmc/            # EPMC 数据源封装
│   └── boot/            # Spring Boot 自动配置
└── README.md            # 使用指南
```

## 核心特性

### ✅ 已实现功能
1. **数据源客户端**
   - PubMed：ESearch（JSON 优先）、EFetch（XML/JSON 按需）
   - Europe PMC：Search（原生 JSON）
   - 返回对象提供结构化字段，同时保留 `raw()` 访问原始 `JsonNode`
2. **配置管理**
   - 调用参数 > 本地配置（Nacos/application.yml）两级优先级
   - `DefaultConfigProvider` 负责兜底配置并完成 URL/Headers 归一化
3. **网关集成**
   - 统一通过 `EgressGatewayClient` 调用，封装 ResilienceConfig（超时 / 重试 / 限流）
   - 自动校验 response envelope，统一抛出 `ProvenanceClientException`
4. **自动配置与降级**
   - `@AutoConfiguration` + 条件装配（可选 Micrometer、网关缺失降级 NoOp）
   - 内置 `provenanceObjectMapper` Bean，保证 JSON 解析行为一致
5. **可观测性**
   - Micrometer 指标：延迟、成功数、失败数（按数据源/接口打标签）
   - 日志规范：`[PROVENANCE][CORE|BOOT|INTERNAL]` 前缀
6. **基础测试**
   - 单元测试覆盖配置构建、请求构建与 JSON 解析关键路径

### 🚧 后续迭代建议
- 扩充解析测试样本覆盖更多 PubMed/EPMC 边界场景
- 增加集成测试（WireMock / Testcontainers）
- 响应缓存、API Key 管理、自动分页与批量处理策略
- 新增数据源（Crossref、Scopus 等）

## 快速开始

### 1. 添加依赖
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-provenance</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置 application.yml（示例）
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

    private final PubMedClient pubMedClient;

    public LiteratureService(PubMedClient pubMedClient) {
        this.pubMedClient = pubMedClient;
    }

    public void searchPubMed() {
        ESearchRequest request = new ESearchRequest("pubmed", "cancer AND therapy");
        ESearchResponse response = pubMedClient.esearch(request);

        System.out.println("Total count: " + response.result().count());
        System.out.println("ID list: " + response.result().idList());
        // 原始 JSON 可通过 response.raw() 获取
    }

    public void fetchDetails(String ids) {
        EFetchRequest request = new EFetchRequest("pubmed", ids);
        EFetchResponse response = pubMedClient.efetch(request);
        response.articles().forEach(article -> System.out.println(article.pmid()));
    }
}
```

## 技术栈与版本
- **Java** 21  + Spring Boot 3.2.4
- **Spring Cloud** 2023.0.1 + Spring Cloud Alibaba 2023.0.1.0
- **Jackson**（JSON / XML），`provenanceObjectMapper` 统一配置
- **Micrometer**（可选）用于指标收集
- **Lombok** 辅助样板代码消除

## 关键指标
| 指标 | 数值 |
|------|------|
| Java 源文件 | 35 |
| 单元测试 | 9 例（覆盖核心解析/配置逻辑） |
| 客户端接口 | 2（PubMedClient、EPMCClient） |
| Request 类 | 3 |
| Response 类 | 10+（包含嵌套对象） |
| 自动配置 Bean | 5 |

## 状态速览
- ✅ 功能实现：核心接口、配置兜底、降级与指标均已就绪
- ✅ 构建状态：`mvn -pl patra-spring-boot-starter-provenance -am test` 通过
- ✅ 文档同步：README、provenance-client.md、task.md 已更新
- 🔄 后续重点：业务侧集成、端到端测试、扩展功能评估

---
**维护人**：linqibin  
**版本**：0.1.0-SNAPSHOT  
**更新时间**：2025-10-06
