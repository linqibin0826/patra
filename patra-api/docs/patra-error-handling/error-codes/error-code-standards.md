# 错误代码设计规范

Patra 错误处理系统的错误代码设计规范和命名约定，确保全平台错误代码的一致性和可维护性。

## 目录

1. [错误代码格式](#错误代码格式)
2. [命名约定](#命名约定)
3. [分类体系](#分类体系)
4. [HTTP状态码映射](#http状态码映射)
5. [错误代码生命周期](#错误代码生命周期)
6. [最佳实践](#最佳实践)

## 错误代码格式

### 基本格式

所有错误代码遵循统一格式：`{SERVICE_PREFIX}-{NNNN}`

- **SERVICE_PREFIX**: 2-4字符服务标识符
- **NNNN**: 4位数字代码，具有语义分组

### 示例

```
REG-1401  → Registry服务，字典类型未找到
ORD-2001  → Order服务，订单未找到
INV-3001  → Inventory服务，库存不足
USR-4001  → User服务，用户未找到
PAY-5001  → Payment服务，支付失败
```

## 命名约定

### 服务前缀规范

| 服务类型 | 前缀 | 示例 | 说明 |
|---------|------|------|------|
| Registry | REG | REG-1401 | 注册表/字典服务 |
| Order | ORD | ORD-2001 | 订单管理服务 |
| Inventory | INV | INV-3001 | 库存管理服务 |
| User | USR | USR-4001 | 用户管理服务 |
| Payment | PAY | PAY-5001 | 支付服务 |
| Notification | NOT | NOT-6001 | 通知服务 |
| Analytics | ANA | ANA-7001 | 分析服务 |
| Gateway | GW | GW-8001 | 网关服务 |

### 前缀选择原则

1. **简洁明了** - 2-4个字符，易于识别
2. **语义清晰** - 能够直观反映服务功能
3. **全局唯一** - 避免不同服务使用相同前缀
4. **稳定不变** - 一旦确定不轻易更改

## 分类体系

### 数字代码分组

#### 0xxx 系列 - HTTP对齐的通用代码

```
{PREFIX}-0400  → Bad Request (400)
{PREFIX}-0401  → Unauthorized (401)
{PREFIX}-0403  → Forbidden (403)
{PREFIX}-0404  → Not Found (404)
{PREFIX}-0409  → Conflict (409)
{PREFIX}-0422  → Unprocessable Entity (422)
{PREFIX}-0429  → Too Many Requests (429)
{PREFIX}-0500  → Internal Server Error (500)
{PREFIX}-0503  → Service Unavailable (503)
{PREFIX}-0504  → Gateway Timeout (504)
```

#### 1xxx-9xxx 系列 - 业务特定代码

按功能领域分组，每个服务可以自定义分组策略：

**Registry 服务示例**：
```
REG-14xx  → 字典相关错误
REG-15xx  → 注册表通用错误
REG-16xx  → 配置相关错误
REG-17xx  → 权限相关错误
```

**Order 服务示例**：
```
ORD-11xx  → 订单管理错误
ORD-12xx  → 支付处理错误
ORD-13xx  → 库存相关错误
ORD-14xx  → 物流相关错误
```

### 功能分组建议

| 分组 | 范围 | 用途 | 示例 |
|------|------|------|------|
| 1xxx | 核心业务操作 | 主要业务实体的CRUD操作 | 用户、订单、商品 |
| 2xxx | 次要业务操作 | 辅助业务功能 | 评论、收藏、推荐 |
| 3xxx | 集成操作 | 外部系统集成 | 支付网关、物流API |
| 4xxx | 管理操作 | 管理员功能 | 配置管理、用户管理 |
| 5xxx | 系统操作 | 系统级功能 | 监控、日志、缓存 |
| 6xxx-9xxx | 扩展预留 | 未来功能扩展 | 待定 |

## HTTP状态码映射

### 自动映射规则

系统根据错误特征自动映射HTTP状态码：

```java
// 错误特征到HTTP状态码的映射
ErrorTrait.NOT_FOUND        → 404 Not Found
ErrorTrait.CONFLICT         → 409 Conflict
ErrorTrait.RULE_VIOLATION   → 422 Unprocessable Entity
ErrorTrait.UNAUTHORIZED     → 401 Unauthorized
ErrorTrait.FORBIDDEN        → 403 Forbidden
ErrorTrait.RATE_LIMITED     → 429 Too Many Requests
ErrorTrait.TIMEOUT          → 504 Gateway Timeout
```

### 后缀启发式映射

对于未实现错误特征的异常，使用命名约定：

```java
// 异常类名模式到HTTP状态码的映射
*NotFound*          → 404
*AlreadyExists*     → 409
*Conflict*          → 409
*Validation*        → 422
*Unauthorized*      → 401
*Forbidden*         → 403
*Timeout*           → 504
```

### 错误代码后缀映射

```java
// 错误代码后缀到HTTP状态码的映射
*-0404, *-NOT-FOUND     → 404
*-0409, *-CONFLICT      → 409
*-0422, *-VALIDATION    → 422
*-0401, *-UNAUTHORIZED  → 401
*-0403, *-FORBIDDEN     → 403
*-0429, *-QUOTA         → 429
*-0504, *-TIMEOUT       → 504
```

## 错误代码生命周期

### 1. 创建阶段

```java
// 1. 在错误代码枚举中添加新代码
public enum RegistryErrorCode implements ErrorCodeLike {
    // 现有代码...
    
    // 新增代码
    REG_1410("REG-1410"); // Dictionary Import Failed
    
    // 构造函数和方法...
}

// 2. 创建对应的领域异常
public class DictionaryImportFailedException extends RegistryRuleViolation {
    private final String importId;
    private final String reason;
    
    public DictionaryImportFailedException(String importId, String reason) {
        super(String.format("Dictionary import failed: importId=%s, reason=%s", importId, reason));
        this.importId = importId;
        this.reason = reason;
    }
}

// 3. 在错误映射贡献者中添加映射
@Component
public class RegistryErrorMappingContributor implements ErrorMappingContributor {
    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        // 现有映射...
        
        if (exception instanceof DictionaryImportFailedException) {
            return Optional.of(RegistryErrorCode.REG_1410);
        }
        
        return Optional.empty();
    }
}
```

### 2. 文档化阶段

```markdown
#### 字典操作 (14xx 系列)

| 代码 | HTTP状态 | 描述 | 领域异常 | 用途 |
|------|----------|------|----------|------|
| `REG-1410` | 422 | Dictionary Import Failed | `DictionaryImportFailedException` | 字典导入操作失败 |

##### 使用示例

```java
// 示例用法
throw new DictionaryImportFailedException("import-123", "Invalid format");
// 结果：REG-1410 with 422 status
```

##### 响应示例

```json
{
  "type": "https://errors.patra.com/reg-1410",
  "title": "REG-1410",
  "status": 422,
  "detail": "Dictionary import failed: importId=import-123, reason=Invalid format",
  "code": "REG-1410",
  "traceId": "abc123def456",
  "path": "/api/registry/dictionaries/import",
  "timestamp": "2025-09-20T10:30:00Z"
}
```
```

### 3. 弃用阶段

```java
public enum RegistryErrorCode implements ErrorCodeLike {
    
    /**
     * @deprecated 使用 REG_1411 替代。将在 2.0.0 版本中移除。
     */
    @Deprecated(since = "1.5.0", forRemoval = true)
    REG_1410("REG-1410"),
    
    // 新的替代代码
    REG_1411("REG-1411"); // Dictionary Import Failed - Enhanced
}
```

### 4. 移除阶段

- **最小保留期**：6个月
- **版本要求**：主版本升级时才能移除
- **迁移指南**：提供详细的迁移文档

## 最佳实践

### 1. 错误代码设计原则

#### 语义化命名
```java
// ✅ 好的做法 - 语义清晰
REG_1401("REG-1401")  // Dictionary Type Not Found
REG_1402("REG-1402")  // Dictionary Item Not Found

// ❌ 避免的做法 - 语义不明
REG_1001("REG-1001")  // Generic Error
REG_9999("REG-9999")  // Unknown Error
```

#### 分组一致性
```java
// ✅ 好的做法 - 按功能分组
REG_1401("REG-1401")  // Dictionary Type Not Found
REG_1402("REG-1402")  // Dictionary Item Not Found
REG_1403("REG-1403")  // Dictionary Item Disabled

// ❌ 避免的做法 - 分组混乱
REG_1401("REG-1401")  // Dictionary Type Not Found
REG_2001("REG-2001")  // Dictionary Item Not Found (应该是14xx)
```

### 2. 错误消息设计

#### 信息丰富但不敏感
```java
// ✅ 好的做法
"Dictionary type not found: COUNTRY"
"Dictionary item not found: typeCode=COUNTRY, itemCode=US"

// ❌ 避免的做法
"Error occurred"  // 信息不足
"Database query failed: SELECT * FROM dict_types WHERE code='COUNTRY'"  // 暴露内部实现
```

#### 用户友好
```java
// ✅ 好的做法 - 面向用户
"The requested dictionary type 'COUNTRY' does not exist"

// ❌ 避免的做法 - 技术术语
"DictionaryType entity with primary key 'COUNTRY' not found in persistence context"
```

### 3. 错误代码演进

#### 向后兼容
```java
// ✅ 好的做法 - 保持兼容
public enum RegistryErrorCode implements ErrorCodeLike {
    // 保留旧代码以保持兼容性
    REG_1401("REG-1401"),
    
    // 添加新代码
    REG_1411("REG-1411");
}

// ❌ 避免的做法 - 破坏兼容性
// 直接修改现有代码的含义或删除代码
```

#### 渐进式增强
```java
// ✅ 好的做法 - 渐进增强
// 阶段1：添加新代码
REG_1411("REG-1411")

// 阶段2：标记旧代码为弃用
@Deprecated REG_1410("REG-1410")

// 阶段3：在主版本升级时移除
// 移除 REG_1410
```

### 4. 文档维护

#### 完整的错误代码目录
- 每个错误代码都有详细说明
- 包含使用示例和响应示例
- 定期更新和审查

#### 变更日志
- 记录所有错误代码的变更
- 包含添加、修改、弃用、移除的历史
- 提供迁移指南

### 5. 监控和分析

#### 错误代码指标
```yaml
# Prometheus 指标示例
patra_error_codes_total{service="registry", code="REG-1401", status="404"} 42
patra_error_codes_total{service="registry", code="REG-1404", status="409"} 5
```

#### 告警规则
```yaml
# 高错误率告警
- alert: HighErrorRate
  expr: rate(patra_error_codes_total{status=~"5.."}[5m]) > 0.1
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "High server error rate for {{ $labels.service }}"

# 特定业务错误告警
- alert: HighDictionaryNotFoundRate
  expr: rate(patra_error_codes_total{code="REG-1401"}[5m]) > 1.0
  for: 5m
  labels:
    severity: info
  annotations:
    summary: "High dictionary not found rate"
```

通过遵循这些设计规范和最佳实践，可以确保错误代码体系的一致性、可维护性和可扩展性。