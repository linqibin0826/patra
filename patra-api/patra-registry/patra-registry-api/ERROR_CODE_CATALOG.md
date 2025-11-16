# 注册中心服务错误代码目录

本文档提供注册中心服务所有错误代码的完整目录。错误代码遵循 `REG-NNNN` 格式,旨在为所有注册中心服务操作提供一致的、机器可读的错误识别。

## 错误代码格式

所有注册中心错误代码遵循结构化格式: **`REG-NNNN`**

- **`REG`** - 注册中心服务上下文前缀
- **`NNNN`** - 四位数字代码

## 错误代码分类

### 业务特定代码(1xxx 系列)

这些代码代表注册中心服务操作特定的业务逻辑错误,直接映射到领域异常。

#### 字典操作(14xx 系列)

| 代码 | 领域异常 | 描述 | 用途 | 示例场景 |
|------|------------------|-------------|-------|------------------|
| `REG-1401` | `DictionaryNotFoundException` | 字典类型未找到 | 指定的字典类型无法找到 | 访问字典类型 "unknown-type" |
| `REG-1402` | `DictionaryNotFoundException` | 字典项未找到 | 指定的字典项无法找到 | 访问字典类型 "sources" 中的项 "missing-item" |
| `REG-1403` | `DictionaryItemDisabled` | 字典项已禁用 | 指定的字典项已被禁用 | 尝试使用已禁用的字典项 |
| `REG-1404` | `DictionaryTypeAlreadyExists` | 字典类型已存在 | 尝试创建已存在的字典类型 | 当字典类型 "sources" 已存在时尝试创建它 |
| `REG-1405` | `DictionaryItemAlreadyExists` | 字典项已存在 | 尝试创建已存在的字典项 | 当类型 "sources" 中的项 "pubmed" 已存在时尝试创建它 |
| `REG-1406` | `DictionaryTypeDisabled` | 字典类型已禁用 | 指定的字典类型已被禁用 | 尝试使用已禁用的字典类型 |
| `REG-1407` | `DictionaryValidationException` | 字典验证错误 | 字典数据验证失败 | 提交格式无效的字典数据 |
| `REG-1408` | `DictionaryDefaultItemMissing` | 字典默认项缺失 | 字典类型缺少必需的默认项 | 字典类型缺少必需的默认项 |
| `REG-1409` | `DictionaryRepositoryException` | 字典仓储错误 | 数据库或仓储层发生错误 | 字典操作期间数据库连接失败 |

#### 注册中心通用操作(15xx 系列)

| 代码 | 领域异常 | 描述 | 用途 | 示例场景 |
|------|------------------|-------------|-------|------------------|
| `REG-1501` | `RegistryQuotaExceeded` | 注册中心配额超限 | 操作将超出系统配额或限制 | 创建的字典项数量超出系统限制 |

## 错误响应格式

所有注册中心服务错误遵循 RFC 7807 ProblemDetail 格式:

```json
{
  "type": "https://errors.example.com/reg-1001",
  "title": "REG-1001",
  "status": 409,
  "detail": "Namespace already exists: medical-publication",
  "code": "REG-1001",
  "traceId": "abc123def456",
  "path": "/api/registry/namespaces",
  "timestamp": "2025-09-19T10:30:00Z"
}
```

## 使用指南

### 对于 API 消费者

1. **编程处理**: 使用 `code` 字段进行编程错误处理
2. **人类可读消息**: 使用 `detail` 字段作为面向用户的错误消息
3. **调试**: 使用 `traceId` 字段进行调试和支持请求
4. **错误分类**: 使用代码前缀和系列对错误类型进行分类

### 对于开发人员

1. **错误代码选择**: 选择最具体的可用错误代码
2. **回退策略**: 当没有特定业务代码适用时,通过 `HttpStdErrors` 工厂使用 HTTP 对齐代码
3. **错误消息**: 在 `detail` 字段中提供清晰、可操作的错误消息
4. **一致性**: 在所有端点保持一致的错误处理模式

## 仅追加策略

此错误目录遵循**仅追加原则**以确保 API 稳定性:

- 允许: 添加新的错误代码
- 允许: 添加新的文档或示例
- 禁止: 移除现有错误代码
- 禁止: 更改现有错误代码的含义
- 禁止: 修改现有错误代码字符串

## 集成示例

### Java 用法

```java
// 领域异常已存在并映射到错误代码
// 字典操作
throw new DictionaryNotFoundException("sources"); // 映射到 REG-1401
throw new DictionaryNotFoundException("sources", "pubmed"); // 映射到 REG-1402
throw new DictionaryItemDisabled("sources", "disabled-item"); // 映射到 REG-1403
throw new DictionaryTypeAlreadyExists("sources"); // 映射到 REG-1404

// 错误代码可用于应用层映射
public class RegistryErrorCodeMapper {
    public static RegistryErrorCode mapException(RegistryException exception) {
        return switch (exception) {
            case DictionaryNotFoundException dnf when dnf.getItemCode() == null ->
                RegistryErrorCode.REG_1401;
            case DictionaryNotFoundException dnf when dnf.getItemCode() != null ->
                RegistryErrorCode.REG_1402;
            case DictionaryItemDisabled did ->
                RegistryErrorCode.REG_1403;
            case DictionaryTypeAlreadyExists dtae ->
                RegistryErrorCode.REG_1404;
            // ... 其他映射
            default -> RegistryErrorCode.REG_0500; // 回退
        };
    }
}
```

### 客户端处理

```javascript
// JavaScript/TypeScript 客户端处理
switch (error.code) {
    case 'REG-1401':
        // 字典类型未找到
        showError('指定的字典类型无法找到。');
        break;
    case 'REG-1402':
        // 字典项未找到
        showError('指定的字典项无法找到。');
        break;
    case 'REG-1403':
        // 字典项已禁用
        showError('此字典项当前已禁用,无法使用。');
        break;
    case 'REG-1404':
        // 字典类型已存在
        showError('此字典类型名称已被占用。请选择不同的名称。');
        break;
    case 'REG-1405':
        // 字典项已存在
        showError('此字典项在指定类型中已存在。');
        break;
    default:
        showError('发生意外错误。请重试。');
}
```

## 版本历史

| 版本 | 日期 | 变更 |
|---------|------|---------|
| 0.1.0 | 2025-09-19 | 初始错误代码目录,包含 HTTP 对齐和业务特定代码 |

## 支持

关于错误代码的问题或请求新错误代码,请:

1. 首先检查本文档
2. 查看 `RegistryErrorCode.java` 中的源代码
3. 向开发团队创建工单
4. 在调试时包含错误响应中的 `traceId`

---

*本文档从 `RegistryErrorCode` 枚举自动生成。有关最新信息,请参考源代码。*
