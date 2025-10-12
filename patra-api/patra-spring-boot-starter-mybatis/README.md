# patra-spring-boot-starter-mybatis

## Purpose
MyBatis-Plus conveniences with safe defaults: mapper scanning, core plugins, JSON TypeHandlers, and data-layer error mapping into the platform pipeline.

## Auto-Configuration
- `com.patra.starter.mybatis.autoconfig.PatraMybatisAutoConfiguration`
- `com.patra.starter.mybatis.autoconfig.MybatisPluginAutoConfig`

## Beans and Features
- Mapper scanning
  - `MapperScannerConfigurer` scans `com.patra.**.infra.persistence.mapper` by default.
- Plugins (MyBatis-Plus)
  - Pagination (MySQL), Optimistic Locker, Block Attack
- TypeHandlers
  - Registers `JsonToJsonNodeTypeHandler` for `JsonNode` globally (uses Spring `ObjectMapper`).
  - `JsonToMapTypeHandler` available; register via `mybatis-plus.type-handlers-package` if needed.
- Error mapping
  - `DataLayerErrorMappingContributor` maps common SQL errors to standard HTTP codes via `HttpStdErrors`.

## Usage Hints
- Add additional mapper locations/type aliases via standard MyBatis-Plus properties.
- No datasource/transaction configuration is provided; configure per service.

## Examples
Register additional TypeHandlers
```yaml
mybatis-plus:
  type-handlers-package: com.example.app.mybatis.type
```

Persist JSON to `JsonNode`
```java
@TableName("articles")
class ArticleDO {
  private Long id;
  private JsonNode metadata; // handled by JsonToJsonNodeTypeHandler
}
```
