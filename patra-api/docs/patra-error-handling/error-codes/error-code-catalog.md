# 全平台错误代码目录

Patra 平台所有服务的错误代码完整目录，提供可搜索的错误代码参考和使用指南。

## 目录

1. [通用错误代码](#通用错误代码)
2. [Registry 服务错误代码](#registry-服务错误代码)
3. [Order 服务错误代码](#order-服务错误代码)
4. [Inventory 服务错误代码](#inventory-服务错误代码)
5. [User 服务错误代码](#user-服务错误代码)
6. [错误代码搜索](#错误代码搜索)

## 通用错误代码

所有服务都支持的HTTP对齐错误代码：

| 代码模式 | HTTP状态 | 描述 | 使用场景 |
|----------|----------|------|----------|
| `{PREFIX}-0400` | 400 | Bad Request | 请求格式错误 |
| `{PREFIX}-0401` | 401 | Unauthorized | 需要认证 |
| `{PREFIX}-0403` | 403 | Forbidden | 访问被拒绝 |
| `{PREFIX}-0404` | 404 | Not Found | 资源未找到 |
| `{PREFIX}-0409` | 409 | Conflict | 资源冲突 |
| `{PREFIX}-0422` | 422 | Unprocessable Entity | 验证失败 |
| `{PREFIX}-0429` | 429 | Too Many Requests | 限流 |
| `{PREFIX}-0500` | 500 | Internal Server Error | 服务器内部错误 |
| `{PREFIX}-0503` | 503 | Service Unavailable | 服务不可用 |
| `{PREFIX}-0504` | 504 | Gateway Timeout | 网关超时 |

### 响应示例

```json
{
  "type": "https://errors.patra.com/reg-0404",
  "title": "REG-0404",
  "status": 404,
  "detail": "Resource not found",
  "code": "REG-0404",
  "traceId": "abc123def456",
  "path": "/api/registry/resources/test",
  "timestamp": "2025-09-20T10:30:00Z"
}
```

## Registry 服务错误代码

Registry 服务管理字典、模式和配置数据。

### 字典操作 (14xx 系列)

| 代码 | HTTP状态 | 描述 | 领域异常 | 用途 |
|------|----------|------|----------|------|
| `REG-1401` | 404 | Dictionary Type Not Found | `DictionaryNotFoundException` | 字典类型不存在 |
| `REG-1402` | 404 | Dictionary Item Not Found | `DictionaryNotFoundException` | 字典项不存在 |
| `REG-1403` | 422 | Dictionary Item Disabled | `DictionaryItemDisabled` | 字典项已禁用 |
| `REG-1404` | 409 | Dictionary Type Already Exists | `DictionaryTypeAlreadyExists` | 字典类型已存在 |
| `REG-1405` | 409 | Dictionary Item Already Exists | `DictionaryItemAlreadyExists` | 字典项已存在 |
| `REG-1406` | 422 | Dictionary Type Disabled | `DictionaryTypeDisabled` | 字典类型已禁用 |
| `REG-1407` | 422 | Dictionary Validation Error | `DictionaryValidationException` | 字典验证失败 |
| `REG-1408` | 422 | Dictionary Default Item Missing | `DictionaryDefaultItemMissing` | 缺少默认字典项 |
| `REG-1409` | 500 | Dictionary Repository Error | `DictionaryRepositoryException` | 数据库操作失败 |

### Registry 通用操作 (15xx 系列)

| 代码 | HTTP状态 | 描述 | 领域异常 | 用途 |
|------|----------|------|----------|------|
| `REG-1501` | 429 | Registry Quota Exceeded | `RegistryQuotaExceeded` | 服务配额超限 |

### 使用示例

```java
// 字典类型未找到
throw new DictionaryNotFoundException("COUNTRY");
// 结果：REG-1401 with 404 status

// 字典项未找到  
throw new DictionaryNotFoundException("COUNTRY", "US");
// 结果：REG-1402 with 404 status

// 字典项已禁用
throw new DictionaryItemDisabled("COUNTRY", "US");
// 结果：REG-1403 with 422 status
```

## Order 服务错误代码

*注意：这是示例，实际的Order服务错误代码需要根据具体业务需求定义*

### 订单管理 (11xx 系列)

| 代码 | HTTP状态 | 描述 | 领域异常 | 用途 |
|------|----------|------|----------|------|
| `ORD-1101` | 404 | Order Not Found | `OrderNotFoundException` | 订单不存在 |
| `ORD-1102` | 409 | Order Already Exists | `OrderAlreadyExistsException` | 重复创建订单 |
| `ORD-1103` | 422 | Order Invalid Status | `OrderInvalidStatusException` | 无效状态转换 |
| `ORD-1104` | 422 | Order Item Invalid | `OrderItemInvalidException` | 无效订单项 |
| `ORD-1105` | 409 | Order Cannot Be Modified | `OrderCannotBeModifiedException` | 订单不可修改 |

### 支付处理 (12xx 系列)

| 代码 | HTTP状态 | 描述 | 领域异常 | 用途 |
|------|----------|------|----------|------|
| `ORD-1201` | 422 | Payment Method Invalid | `PaymentMethodInvalidException` | 无效支付方式 |
| `ORD-1202` | 422 | Payment Amount Invalid | `PaymentAmountInvalidException` | 无效支付金额 |
| `ORD-1203` | 409 | Payment Already Processed | `PaymentAlreadyProcessedException` | 支付已处理 |
| `ORD-1204` | 503 | Payment Gateway Unavailable | `PaymentGatewayUnavailableException` | 支付网关不可用 |

## Inventory 服务错误代码

*注意：这是示例，实际的Inventory服务错误代码需要根据具体业务需求定义*

### 库存管理 (21xx 系列)

| 代码 | HTTP状态 | 描述 | 领域异常 | 用途 |
|------|----------|------|----------|------|
| `INV-2101` | 404 | Product Not Found | `ProductNotFoundException` | 商品不存在 |
| `INV-2102` | 422 | Insufficient Stock | `InsufficientStockException` | 库存不足 |
| `INV-2103` | 409 | Stock Already Reserved | `StockAlreadyReservedException` | 库存已预留 |
| `INV-2104` | 422 | Invalid Stock Quantity | `InvalidStockQuantityException` | 无效库存数量 |

## User 服务错误代码

*注意：这是示例，实际的User服务错误代码需要根据具体业务需求定义*

### 用户管理 (31xx 系列)

| 代码 | HTTP状态 | 描述 | 领域异常 | 用途 |
|------|----------|------|----------|------|
| `USR-3101` | 404 | User Not Found | `UserNotFoundException` | 用户不存在 |
| `USR-3102` | 409 | User Already Exists | `UserAlreadyExistsException` | 用户已存在 |
| `USR-3103` | 422 | User Validation Failed | `UserValidationException` | 用户数据验证失败 |
| `USR-3104` | 403 | User Account Disabled | `UserAccountDisabledException` | 用户账户已禁用 |
| `USR-3105` | 401 | User Authentication Failed | `UserAuthenticationFailedException` | 用户认证失败 |

## 错误代码搜索

### 按服务搜索

- **Registry (REG-\*)**: [Registry 错误代码详情](registry-error-codes.md)
- **Order (ORD-\*)**: Order 服务错误代码（待实现）
- **Inventory (INV-\*)**: Inventory 服务错误代码（待实现）
- **User (USR-\*)**: User 服务错误代码（待实现）

### 按HTTP状态码搜索

- **404 错误**: REG-1401, REG-1402, ORD-1101, INV-2101, USR-3101
- **409 错误**: REG-1404, REG-1405, ORD-1102, ORD-1105, INV-2103, USR-3102
- **422 错误**: REG-1403, REG-1406, REG-1407, ORD-1103, ORD-1104, INV-2102, USR-3103
- **429 错误**: REG-1501
- **500 错误**: REG-1409
- **503 错误**: ORD-1204

### 按功能领域搜索

- **字典管理**: REG-14xx 系列
- **订单管理**: ORD-11xx 系列
- **支付处理**: ORD-12xx 系列
- **库存管理**: INV-21xx 系列
- **用户管理**: USR-31xx 系列

## 客户端错误处理示例

### Java 客户端

```java
@Service
public class MultiServiceClient {
    
    private final RegistryClient registryClient;
    private final OrderClient orderClient;
    
    public void handleRegistryErrors() {
        try {
            registryClient.getDictionaryType("COUNTRY");
        } catch (RemoteCallException ex) {
            switch (ex.getErrorCode()) {
                case "REG-1401":
                    log.debug("Dictionary type not found");
                    break;
                case "REG-1403":
                    log.warn("Dictionary item disabled");
                    break;
                default:
                    log.error("Unexpected registry error: {}", ex.getErrorCode());
            }
        }
    }
    
    public void handleOrderErrors() {
        try {
            orderClient.getOrder("12345");
        } catch (RemoteCallException ex) {
            if (RemoteErrorHelper.is(ex, "ORD-1101")) {
                throw new OrderNotFoundException("12345");
            }
            
            if (RemoteErrorHelper.is(ex, "ORD-1103")) {
                throw new InvalidOrderStatusException(ex.getMessage());
            }
        }
    }
}
```

### JavaScript 客户端

```javascript
class PatraApiClient {
    
    async handleApiError(response) {
        if (!response.ok) {
            const error = await response.json();
            const code = error.code;
            
            // Registry 错误处理
            if (code.startsWith('REG-')) {
                return this.handleRegistryError(code, error);
            }
            
            // Order 错误处理
            if (code.startsWith('ORD-')) {
                return this.handleOrderError(code, error);
            }
            
            // 通用错误处理
            return this.handleGenericError(code, error);
        }
    }
    
    handleRegistryError(code, error) {
        switch (code) {
            case 'REG-1401':
                console.debug('Dictionary type not found');
                return null;
            case 'REG-1402':
                console.debug('Dictionary item not found');
                return null;
            case 'REG-1403':
                console.warn('Dictionary item disabled');
                return null;
            default:
                throw new Error(`Registry error: ${code} - ${error.detail}`);
        }
    }
    
    handleOrderError(code, error) {
        switch (code) {
            case 'ORD-1101':
                throw new OrderNotFoundError(error.detail);
            case 'ORD-1103':
                throw new InvalidOrderStatusError(error.detail);
            default:
                throw new Error(`Order error: ${code} - ${error.detail}`);
        }
    }
}
```

## 监控和告警

### Prometheus 指标

```yaml
# 错误代码分布指标
patra_error_codes_total{service="registry", code="REG-1401", status="404"} 42
patra_error_codes_total{service="order", code="ORD-1101", status="404"} 15
patra_error_codes_total{service="inventory", code="INV-2102", status="422"} 8
```

### 告警规则示例

```yaml
groups:
- name: patra-error-codes
  rules:
  # 高服务器错误率
  - alert: HighServerErrorRate
    expr: rate(patra_error_codes_total{status=~"5.."}[5m]) > 0.1
    for: 2m
    labels:
      severity: warning
    annotations:
      summary: "High server error rate for {{ $labels.service }}"
      description: "Service {{ $labels.service }} has {{ $value }} server errors per second"

  # 特定业务错误率
  - alert: HighDictionaryNotFoundRate
    expr: rate(patra_error_codes_total{code="REG-1401"}[5m]) > 1.0
    for: 5m
    labels:
      severity: info
    annotations:
      summary: "High dictionary not found rate"
      description: "Registry service has {{ $value }} dictionary not found errors per second"
      
  # 库存不足告警
  - alert: HighInsufficientStockRate
    expr: rate(patra_error_codes_total{code="INV-2102"}[5m]) > 0.5
    for: 3m
    labels:
      severity: warning
    annotations:
      summary: "High insufficient stock error rate"
      description: "Inventory service has {{ $value }} insufficient stock errors per second"
```

这个错误代码目录提供了全平台错误代码的统一视图，便于开发者查找和使用特定的错误代码。