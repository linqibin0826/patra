# Registry 服务错误代码

Registry 服务的完整错误代码目录，包括字典管理、配置管理等功能领域的详细错误代码说明。

## 目录

1. [错误代码概览](#错误代码概览)
2. [字典操作错误 (14xx)](#字典操作错误-14xx)
3. [Registry 通用错误 (15xx)](#registry-通用错误-15xx)
4. [使用示例](#使用示例)
5. [客户端处理](#客户端处理)
6. [监控和告警](#监控和告警)

## 错误代码概览

Registry 服务使用 `REG` 作为服务前缀，错误代码格式为 `REG-NNNN`。

### 错误代码分组

| 分组 | 范围 | 功能领域 | 示例 |
|------|------|----------|------|
| 通用HTTP错误 | REG-0xxx | HTTP状态对齐 | REG-0404, REG-0409 |
| 字典操作 | REG-14xx | 字典类型和字典项管理 | REG-1401, REG-1402 |
| Registry通用 | REG-15xx | 注册表通用功能 | REG-1501 |
| 配置管理 | REG-16xx | 配置相关操作 | REG-1601 (预留) |
| 权限管理 | REG-17xx | 权限和访问控制 | REG-1701 (预留) |

## 字典操作错误 (14xx)

字典管理是 Registry 服务的核心功能，包括字典类型和字典项的 CRUD 操作。

### REG-1401 - Dictionary Type Not Found

**描述**: 请求的字典类型不存在

**HTTP状态码**: 404 Not Found

**领域异常**: `DictionaryNotFoundException`

**触发条件**:
- 查询不存在的字典类型
- 引用已删除的字典类型

**响应示例**:
```json
{
  "type": "https://errors.patra.com/reg-1401",
  "title": "REG-1401",
  "status": 404,
  "detail": "Dictionary type not found: COUNTRY",
  "code": "REG-1401",
  "traceId": "abc123def456",
  "path": "/api/registry/dictionaries/COUNTRY",
  "timestamp": "2025-09-20T10:30:00Z",
  "typeCode": "COUNTRY"
}
```

**Java异常示例**:
```java
throw new DictionaryNotFoundException("COUNTRY");
```

### REG-1402 - Dictionary Item Not Found

**描述**: 请求的字典项不存在

**HTTP状态码**: 404 Not Found

**领域异常**: `DictionaryNotFoundException`

**触发条件**:
- 查询不存在的字典项
- 引用已删除的字典项

**响应示例**:
```json
{
  "type": "https://errors.patra.com/reg-1402",
  "title": "REG-1402",
  "status": 404,
  "detail": "Dictionary item not found: typeCode=COUNTRY, itemCode=XX",
  "code": "REG-1402",
  "traceId": "abc123def456",
  "path": "/api/registry/dictionaries/COUNTRY/items/XX",
  "timestamp": "2025-09-20T10:30:00Z",
  "typeCode": "COUNTRY",
  "itemCode": "XX"
}
```

**Java异常示例**:
```java
throw new DictionaryNotFoundException("COUNTRY", "XX");
```

### REG-1403 - Dictionary Item Disabled

**描述**: 字典项已被禁用，不可使用

**HTTP状态码**: 422 Unprocessable Entity

**领域异常**: `DictionaryItemDisabled`

**触发条件**:
- 尝试使用已禁用的字典项
- 在业务流程中引用禁用的字典项

**响应示例**:
```json
{
  "type": "https://errors.patra.com/reg-1403",
  "title": "REG-1403",
  "status": 422,
  "detail": "Dictionary item is disabled: typeCode=COUNTRY, itemCode=US",
  "code": "REG-1403",
  "traceId": "abc123def456",
  "path": "/api/registry/dictionaries/COUNTRY/items/US",
  "timestamp": "2025-09-20T10:30:00Z",
  "typeCode": "COUNTRY",
  "itemCode": "US",
  "disabledAt": "2025-09-19T15:30:00Z",
  "reason": "Temporarily disabled for maintenance"
}
```

**Java异常示例**:
```java
throw new DictionaryItemDisabled("COUNTRY", "US");
```

### REG-1404 - Dictionary Type Already Exists

**描述**: 尝试创建已存在的字典类型

**HTTP状态码**: 409 Conflict

**领域异常**: `DictionaryTypeAlreadyExists`

**触发条件**:
- 创建重复的字典类型
- 导入包含重复类型的数据

**响应示例**:
```json
{
  "type": "https://errors.patra.com/reg-1404",
  "title": "REG-1404",
  "status": 409,
  "detail": "Dictionary type already exists: COUNTRY",
  "code": "REG-1404",
  "traceId": "abc123def456",
  "path": "/api/registry/dictionaries",
  "timestamp": "2025-09-20T10:30:00Z",
  "typeCode": "COUNTRY",
  "existingSince": "2025-01-15T09:00:00Z"
}
```

**Java异常示例**:
```java
throw new DictionaryTypeAlreadyExists("COUNTRY");
```

### REG-1405 - Dictionary Item Already Exists

**描述**: 尝试创建已存在的字典项

**HTTP状态码**: 409 Conflict

**领域异常**: `DictionaryItemAlreadyExists`

**触发条件**:
- 在同一字典类型下创建重复的字典项
- 批量导入包含重复项的数据

**响应示例**:
```json
{
  "type": "https://errors.patra.com/reg-1405",
  "title": "REG-1405",
  "status": 409,
  "detail": "Dictionary item already exists: typeCode=COUNTRY, itemCode=US",
  "code": "REG-1405",
  "traceId": "abc123def456",
  "path": "/api/registry/dictionaries/COUNTRY/items",
  "timestamp": "2025-09-20T10:30:00Z",
  "typeCode": "COUNTRY",
  "itemCode": "US"
}
```

**Java异常示例**:
```java
throw new DictionaryItemAlreadyExists("COUNTRY", "US");
```

### REG-1406 - Dictionary Type Disabled

**描述**: 字典类型已被禁用

**HTTP状态码**: 422 Unprocessable Entity

**领域异常**: `DictionaryTypeDisabled`

**触发条件**:
- 尝试访问已禁用的字典类型
- 在禁用的字典类型下创建字典项

**响应示例**:
```json
{
  "type": "https://errors.patra.com/reg-1406",
  "title": "REG-1406",
  "status": 422,
  "detail": "Dictionary type is disabled: COUNTRY",
  "code": "REG-1406",
  "traceId": "abc123def456",
  "path": "/api/registry/dictionaries/COUNTRY",
  "timestamp": "2025-09-20T10:30:00Z",
  "typeCode": "COUNTRY",
  "disabledAt": "2025-09-18T14:00:00Z"
}
```

### REG-1407 - Dictionary Validation Error

**描述**: 字典数据验证失败

**HTTP状态码**: 422 Unprocessable Entity

**领域异常**: `DictionaryValidationException`

**触发条件**:
- 字典类型或字典项数据不符合验证规则
- 必填字段缺失或格式错误

**响应示例**:
```json
{
  "type": "https://errors.patra.com/reg-1407",
  "title": "REG-1407",
  "status": 422,
  "detail": "Dictionary validation failed",
  "code": "REG-1407",
  "traceId": "abc123def456",
  "path": "/api/registry/dictionaries",
  "timestamp": "2025-09-20T10:30:00Z",
  "errors": [
    {
      "field": "typeCode",
      "rejectedValue": "",
      "message": "Type code cannot be empty"
    },
    {
      "field": "name",
      "rejectedValue": "A very long name that exceeds the maximum allowed length...",
      "message": "Name must not exceed 100 characters"
    }
  ]
}
```

### REG-1408 - Dictionary Default Item Missing

**描述**: 字典类型缺少必需的默认字典项

**HTTP状态码**: 422 Unprocessable Entity

**领域异常**: `DictionaryDefaultItemMissing`

**触发条件**:
- 删除字典类型的默认项
- 禁用必需的默认项

**响应示例**:
```json
{
  "type": "https://errors.patra.com/reg-1408",
  "title": "REG-1408",
  "status": 422,
  "detail": "Dictionary default item missing: typeCode=STATUS, defaultItemCode=ACTIVE",
  "code": "REG-1408",
  "traceId": "abc123def456",
  "path": "/api/registry/dictionaries/STATUS/items/ACTIVE",
  "timestamp": "2025-09-20T10:30:00Z",
  "typeCode": "STATUS",
  "defaultItemCode": "ACTIVE"
}
```

### REG-1409 - Dictionary Repository Error

**描述**: 字典数据库操作失败

**HTTP状态码**: 500 Internal Server Error

**领域异常**: `DictionaryRepositoryException`

**触发条件**:
- 数据库连接失败
- SQL执行错误
- 数据完整性约束违反

**响应示例**:
```json
{
  "type": "https://errors.patra.com/reg-1409",
  "title": "REG-1409",
  "status": 500,
  "detail": "Dictionary repository operation failed",
  "code": "REG-1409",
  "traceId": "abc123def456",
  "path": "/api/registry/dictionaries/COUNTRY",
  "timestamp": "2025-09-20T10:30:00Z",
  "operation": "findByTypeCode",
  "retryable": true
}
```

## Registry 通用错误 (15xx)

### REG-1501 - Registry Quota Exceeded

**描述**: Registry 服务配额超限

**HTTP状态码**: 429 Too Many Requests

**领域异常**: `RegistryQuotaExceeded`

**触发条件**:
- API调用频率超过限制
- 数据存储量超过配额
- 并发连接数超限

**响应示例**:
```json
{
  "type": "https://errors.patra.com/reg-1501",
  "title": "REG-1501",
  "status": 429,
  "detail": "Registry service quota exceeded: API rate limit",
  "code": "REG-1501",
  "traceId": "abc123def456",
  "path": "/api/registry/dictionaries",
  "timestamp": "2025-09-20T10:30:00Z",
  "quotaType": "API_RATE_LIMIT",
  "currentUsage": 1000,
  "limit": 1000,
  "resetTime": "2025-09-20T11:00:00Z",
  "retryAfter": 1800
}
```

## 使用示例

### Java 服务端异常处理

```java
@Service
public class DictionaryService {
    
    private final DictionaryRepository dictionaryRepository;
    
    public DictionaryItemDto getDictionaryItem(String typeCode, String itemCode) {
        // 验证字典类型存在
        DictionaryType type = dictionaryRepository.findTypeByCode(typeCode)
            .orElseThrow(() -> new DictionaryNotFoundException(typeCode));
        
        // 检查字典类型是否启用
        if (!type.isEnabled()) {
            throw new DictionaryTypeDisabled(typeCode);
        }
        
        // 查找字典项
        DictionaryItem item = dictionaryRepository.findItemByTypeAndCode(typeCode, itemCode)
            .orElseThrow(() -> new DictionaryNotFoundException(typeCode, itemCode));
        
        // 检查字典项是否启用
        if (!item.isEnabled()) {
            throw new DictionaryItemDisabled(typeCode, itemCode);
        }
        
        return converter.toDto(item);
    }
    
    public DictionaryTypeDto createDictionaryType(CreateDictionaryTypeRequest request) {
        // 检查是否已存在
        if (dictionaryRepository.existsByTypeCode(request.getTypeCode())) {
            throw new DictionaryTypeAlreadyExists(request.getTypeCode());
        }
        
        // 验证请求数据
        validateDictionaryTypeRequest(request);
        
        // 创建字典类型
        DictionaryType type = DictionaryType.builder()
            .typeCode(request.getTypeCode())
            .name(request.getName())
            .description(request.getDescription())
            .enabled(true)
            .build();
        
        try {
            DictionaryType savedType = dictionaryRepository.save(type);
            return converter.toDto(savedType);
        } catch (DataAccessException e) {
            throw new DictionaryRepositoryException("Failed to save dictionary type", e);
        }
    }
    
    private void validateDictionaryTypeRequest(CreateDictionaryTypeRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getTypeCode() == null || request.getTypeCode().trim().isEmpty()) {
            errors.add("Type code is required");
        }
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            errors.add("Name is required");
        }
        
        if (!errors.isEmpty()) {
            throw new DictionaryValidationException("Validation failed: " + String.join(", ", errors));
        }
    }
}
```

## 客户端处理

### Java Feign 客户端

```java
@Service
public class RegistryClientService {
    
    private final RegistryClient registryClient;
    
    public Optional<DictionaryItemDto> getDictionaryItemSafely(String typeCode, String itemCode) {
        try {
            DictionaryItemDto item = registryClient.getDictionaryItem(typeCode, itemCode);
            return Optional.of(item);
        } catch (RemoteCallException ex) {
            return handleRegistryError(ex, typeCode, itemCode);
        }
    }
    
    private Optional<DictionaryItemDto> handleRegistryError(
            RemoteCallException ex, String typeCode, String itemCode) {
        
        switch (ex.getErrorCode()) {
            case "REG-1401":
                log.debug("Dictionary type not found: {}", typeCode);
                return Optional.empty();
                
            case "REG-1402":
                log.debug("Dictionary item not found: typeCode={}, itemCode={}", typeCode, itemCode);
                return Optional.empty();
                
            case "REG-1403":
                log.warn("Dictionary item disabled: typeCode={}, itemCode={}", typeCode, itemCode);
                return Optional.empty();
                
            case "REG-1406":
                log.warn("Dictionary type disabled: {}", typeCode);
                return Optional.empty();
                
            case "REG-1409":
                log.error("Registry repository error: traceId={}, message={}", 
                         ex.getTraceId(), ex.getMessage());
                throw new RegistryServiceException("Registry service error", ex);
                
            case "REG-1501":
                log.warn("Registry quota exceeded: traceId={}", ex.getTraceId());
                throw new RegistryQuotaException("Registry service quota exceeded", ex);
                
            default:
                log.error("Unexpected registry error: code={}, traceId={}, message={}", 
                         ex.getErrorCode(), ex.getTraceId(), ex.getMessage());
                throw new RegistryClientException("Unexpected registry error", ex);
        }
    }
    
    public DictionaryTypeDto createDictionaryTypeWithRetry(CreateDictionaryTypeRequest request) {
        try {
            return registryClient.createDictionaryType(request);
        } catch (RemoteCallException ex) {
            if ("REG-1404".equals(ex.getErrorCode())) {
                // 字典类型已存在，可能是并发创建，尝试获取现有的
                log.info("Dictionary type already exists, fetching existing: {}", request.getTypeCode());
                return registryClient.getDictionaryType(request.getTypeCode());
            }
            
            if ("REG-1407".equals(ex.getErrorCode())) {
                throw new DictionaryValidationException("Dictionary validation failed: " + ex.getMessage(), ex);
            }
            
            throw ex;
        }
    }
}
```

### JavaScript 客户端

```javascript
class RegistryApiClient {
    
    async getDictionaryItem(typeCode, itemCode) {
        try {
            const response = await fetch(`/api/registry/dictionaries/${typeCode}/items/${itemCode}`);
            
            if (response.ok) {
                return await response.json();
            }
            
            const error = await response.json();
            return this.handleRegistryError(error, { typeCode, itemCode });
            
        } catch (err) {
            console.error('Registry API call failed:', err);
            throw new RegistryClientError('Failed to get dictionary item', err);
        }
    }
    
    handleRegistryError(error, context) {
        const { code, detail, traceId } = error;
        
        switch (code) {
            case 'REG-1401':
                console.debug(`Dictionary type not found: ${context.typeCode}`);
                return null;
                
            case 'REG-1402':
                console.debug(`Dictionary item not found: ${context.typeCode}/${context.itemCode}`);
                return null;
                
            case 'REG-1403':
                console.warn(`Dictionary item disabled: ${context.typeCode}/${context.itemCode}`);
                return null;
                
            case 'REG-1404':
                throw new DictionaryConflictError(`Dictionary type already exists: ${context.typeCode}`);
                
            case 'REG-1407':
                throw new DictionaryValidationError(`Validation failed: ${detail}`, error.errors);
                
            case 'REG-1409':
                console.error(`Registry repository error: traceId=${traceId}`);
                throw new RegistryServiceError('Registry service error', { traceId });
                
            case 'REG-1501':
                console.warn(`Registry quota exceeded: traceId=${traceId}`);
                const retryAfter = error.retryAfter || 60;
                throw new RegistryQuotaError('Registry quota exceeded', { retryAfter });
                
            default:
                console.error(`Unexpected registry error: ${code}, traceId=${traceId}`);
                throw new RegistryClientError(`Unexpected error: ${detail}`, { code, traceId });
        }
    }
    
    async createDictionaryTypeWithRetry(request, maxRetries = 3) {
        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return await this.createDictionaryType(request);
            } catch (error) {
                if (error.code === 'REG-1404' && attempt === 1) {
                    // 类型已存在，尝试获取现有的
                    console.info(`Dictionary type exists, fetching: ${request.typeCode}`);
                    return await this.getDictionaryType(request.typeCode);
                }
                
                if (error.code === 'REG-1501' && attempt < maxRetries) {
                    // 配额超限，等待后重试
                    const delay = error.retryAfter * 1000 || 60000;
                    console.warn(`Quota exceeded, retrying in ${delay}ms`);
                    await new Promise(resolve => setTimeout(resolve, delay));
                    continue;
                }
                
                throw error;
            }
        }
    }
}
```

## 监控和告警

### Prometheus 指标

```yaml
# Registry 错误代码分布
patra_error_codes_total{service="registry", code="REG-1401", status="404"} 156
patra_error_codes_total{service="registry", code="REG-1402", status="404"} 89
patra_error_codes_total{service="registry", code="REG-1403", status="422"} 23
patra_error_codes_total{service="registry", code="REG-1404", status="409"} 12
patra_error_codes_total{service="registry", code="REG-1407", status="422"} 45
patra_error_codes_total{service="registry", code="REG-1409", status="500"} 3
patra_error_codes_total{service="registry", code="REG-1501", status="429"} 8
```

### 告警规则

```yaml
groups:
- name: registry-error-alerts
  rules:
  # 字典未找到错误率过高
  - alert: HighDictionaryNotFoundRate
    expr: rate(patra_error_codes_total{service="registry", code=~"REG-140[12]"}[5m]) > 2.0
    for: 3m
    labels:
      severity: warning
      service: registry
    annotations:
      summary: "High dictionary not found error rate"
      description: "Registry service has {{ $value }} dictionary not found errors per second"
      
  # 字典验证错误率过高
  - alert: HighDictionaryValidationErrorRate
    expr: rate(patra_error_codes_total{service="registry", code="REG-1407"}[5m]) > 0.5
    for: 2m
    labels:
      severity: warning
      service: registry
    annotations:
      summary: "High dictionary validation error rate"
      description: "Registry service has {{ $value }} validation errors per second"
      
  # 数据库错误
  - alert: RegistryRepositoryErrors
    expr: rate(patra_error_codes_total{service="registry", code="REG-1409"}[5m]) > 0.1
    for: 1m
    labels:
      severity: critical
      service: registry
    annotations:
      summary: "Registry repository errors detected"
      description: "Registry service has {{ $value }} repository errors per second"
      
  # 配额超限
  - alert: RegistryQuotaExceeded
    expr: rate(patra_error_codes_total{service="registry", code="REG-1501"}[5m]) > 0.2
    for: 1m
    labels:
      severity: warning
      service: registry
    annotations:
      summary: "Registry quota exceeded"
      description: "Registry service quota is being exceeded {{ $value }} times per second"
```

### 错误趋势分析

```promql
# 字典错误趋势（过去24小时）
increase(patra_error_codes_total{service="registry", code=~"REG-14.*"}[24h])

# 错误率趋势（过去1小时，5分钟间隔）
rate(patra_error_codes_total{service="registry"}[5m])

# 最常见的错误代码（过去1小时）
topk(5, sum by (code) (rate(patra_error_codes_total{service="registry"}[1h])))
```

这个详细的 Registry 错误代码文档为开发者提供了完整的错误处理参考，包括每个错误的触发条件、响应格式、处理建议和监控方案。