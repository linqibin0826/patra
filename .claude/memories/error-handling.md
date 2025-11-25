# 异常处理规范

## 异常体系

1. `DomainException`：领域层异常基类，实现 `HasErrorTraits` 接口，携带语义特征（`StandardErrorTrait`）
2. `ApplicationException`：应用层异常基类，携带明确的 `ErrorCodeLike` 错误码
3. `RemoteCallException`：Feign 调用下游服务失败时的统一异常，实现 `HasErrorTraits` 接口

## 各层异常处理规范

1. 领域层（Domain）：继承 `DomainException`，携带 `StandardErrorTrait` 语义特征，禁止依赖框架异常
2. 应用层（Application）：使用 `ApplicationException` 包装领域异常，携带明确的 `ErrorCodeLike`
3. 基础设施层（Infrastructure）：通过 `ErrorMappingContributor` SPI 映射第三方异常（SQL、外部 API 等）
4. 适配器层（Adapter）：捕获 `RemoteCallException`，基于 `ErrorTrait` 语义特征转换为领域异常

## 错误码格式

1. 格式：`{SERVICE}-{0xxx}`，SERVICE 为服务前缀（如 INGEST、REG、CATALOG），0xxx 映射到 HTTP 状态码
2. 常用映射：0404（Not Found）、0409（Conflict）、0422（Unprocessable Entity）、0500（Internal Error）

## ErrorTrait 语义传播

1. 上游服务抛出实现 `HasErrorTraits` 的异常，框架自动输出 `traits` 字段到 RFC 7807 响应
2. 下游服务通过 `RemoteCallException.getErrorTraits()` 获取语义特征，基于特征判断错误类型
3. 推荐使用 `StandardErrorTrait` 枚举：`NOT_FOUND`、`CONFLICT`、`TIMEOUT`、`DEP_UNAVAILABLE` 等

## 错误解析策略优先级

1. `APPLICATION_EXCEPTION`：直接从 `ApplicationException` 获取 `ErrorCodeLike`（可缓存）
2. `CONTRIBUTOR`：SPI 扩展映射，按 `@Order` 执行（不缓存）
3. `TRAIT`：`HasErrorTraits` 语义特征映射（可缓存）
4. `NAMING`：类名后缀启发式（如 `NotFoundException` → 404）（可缓存）
5. `CAUSE`：原因链递归解析，智能跳过包装异常（不缓存）
6. `FALLBACK`：回退策略，客户端错误 422 / 服务器错误 500（可缓存）

## 命名启发式映射

1. `NotFound` → 404，`Conflict` / `AlreadyExists` → 409，`Invalid` / `Validation` → 422
2. `QuotaExceeded` → 429，`Unauthorized` → 401，`Forbidden` → 403，`Timeout` → 504

## Feign 错误处理

1. 捕获 `RemoteCallException` 后，优先使用 `ex.getErrorTraits()` 基于语义特征判断
2. 备选方案：使用 `RemoteErrorHelper` 工具类基于 HTTP 状态码判断（`isNotFound()`、`isServerError()` 等）
3. 禁止直接捕获 `FeignException`，必须使用 `RemoteCallException` 统一处理
