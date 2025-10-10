# API 文档索引

> Papertrace API 设计规范、SpringDoc/OpenAPI 文档

---

## 📄 文档列表

### API 设计规范
- **[Feign API 设计指南](../standards/feign-api-design-guide.md)** - 内部 RPC 契约设计规范
  - 包名规范、DTO 命名、接口设计模式
  - SpringDoc 注解使用（@Operation、@Schema、@ApiResponse）
  - 错误处理与响应封装

- **[跨服务错误处理最佳实践](../standards/platform-error-handling.md#11-跨服务错误链路最佳实践整合)** - 错误传播与处理策略
  - ProblemDetail 统一错误格式
  - 错误码规范、重试策略
  - Feign 异常映射

---

## 🔗 相关文档

### OpenAPI 规范
- **SpringDoc 官方文档**：https://springdoc.org/
- **OpenAPI 3.x 规范**：https://swagger.io/specification/

### 内部 API
- **patra-ingest API**：
  - Feign Client：`patra-ingest-api` 模块
  - Controller：`patra-ingest-adapter/rest/`
  - 在线文档：http://localhost:8080/swagger-ui.html

- **patra-registry API**：
  - Feign Client：`patra-registry-api` 模块
  - Controller：`patra-registry-adapter/rest/`
  - 在线文档：http://localhost:8081/swagger-ui.html

- **patra-egress-gateway API**：
  - Feign Client：`patra-egress-gateway-api` 模块
  - Controller：`patra-egress-gateway-adapter/rest/`
  - 在线文档：http://localhost:8082/swagger-ui.html

---

## 📝 贡献指南

### 添加新 API
1. 在 `{module}-api` 模块定义 Feign Client 接口
2. 在 `{module}-adapter/rest` 实现 Controller
3. 添加 SpringDoc 注解（@Operation、@Schema、@ApiResponse）
4. 提供请求/响应示例（@ExampleObject）
5. 更新 ADR（如果涉及架构决策）

### API 文档模板
```java
/**
 * Provenance Source API
 * 
 * @author linqibin
 * @since 0.1.0
 */
@FeignClient(name = "patra-registry", path = "/api/provenance")
public interface ProvenanceSourceClient {
    
    @Operation(
        summary = "Get provenance source by code",
        description = "Retrieve detailed information of a provenance source",
        tags = {"Provenance"}
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Source found",
            content = @Content(
                schema = @Schema(implementation = ProvenanceSourceDTO.class),
                examples = @ExampleObject("""
                    {
                        "code": "pubmed",
                        "name": "PubMed",
                        "baseUrl": "https://eutils.ncbi.nlm.nih.gov",
                        "status": "ACTIVE"
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Source not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        )
    })
    @GetMapping("/{code}")
    ProvenanceSourceDTO getByCode(@PathVariable String code);
}
```

---

## 🗂️ API 规范

### RESTful 设计原则
- **资源命名**：使用复数名词（`/sources`、`/tasks`、`/plans`）
- **HTTP 方法**：
  - `GET`：查询资源
  - `POST`：创建资源
  - `PUT`：全量更新资源
  - `PATCH`：部分更新资源
  - `DELETE`：删除资源

- **路径设计**：
  - 集合资源：`/api/sources`
  - 单个资源：`/api/sources/{code}`
  - 嵌套资源：`/api/sources/{code}/configs`
  - 操作动词：`/api/tasks/{id}/cancel`（非 CRUD 操作）

### 请求/响应规范
- **Content-Type**：`application/json; charset=UTF-8`
- **请求体**：使用 DTO 封装（`*RequestDTO`、`*Command`）
- **响应体**：统一格式（`*ResponseDTO`、`*Result`）
- **分页**：使用 `Page<T>`（Spring Data）或自定义 `PageResult<T>`
- **错误**：统一使用 `ProblemDetail`（RFC 7807）

### 版本控制
- **URL 版本**：`/api/v1/sources`、`/api/v2/sources`（推荐）
- **Header 版本**：`X-Api-Version: v1`（备选）
- **废弃标记**：`@Deprecated` + `@ApiResponse(description = "Deprecated since v2.0")`

---

## 📊 API 统计

### 端点统计
```bash
# 统计各模块的 API 端点数量
grep -r "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping" --include="*.java" patra-*/patra-*-adapter/src/main/java | wc -l
```

### 文档覆盖率
```bash
# 检查是否所有 API 都有 @Operation 注解
grep -r "@GetMapping\|@PostMapping" --include="*.java" patra-*/patra-*-adapter/src/main/java | \
    while read line; do
        file=$(echo $line | cut -d: -f1)
        grep -q "@Operation" "$file" || echo "Missing @Operation: $file"
    done
```

---

## 🔧 工具与集成

### SpringDoc 配置
```yaml
# application.yml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
  group-configs:
    - group: provenance
      paths-to-match: /api/provenance/**
    - group: schedule
      paths-to-match: /api/schedule/**
```

### 生成 OpenAPI 规范
```bash
# 启动服务后访问
curl http://localhost:8080/v3/api-docs > openapi.json
curl http://localhost:8080/v3/api-docs.yaml > openapi.yaml

# 或使用 Maven 插件
mvn springdoc-openapi:generate
```

### Postman 集成
```bash
# 导入 OpenAPI 规范到 Postman
1. 打开 Postman
2. Import → Link → 输入 http://localhost:8080/v3/api-docs
3. 自动生成 Collection
```

---

**更新记录**

| 版本 | 日期 | 变更说明 | 作者 |
|-----|------|---------|------|
| 1.0 | 2025-10-08 | 初始版本：API 文档索引 | docs-engineer |

---

**许可证**

Copyright © 2025 Papertrace
