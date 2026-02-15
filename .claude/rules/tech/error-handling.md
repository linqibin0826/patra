# 异常处理规范

## 异常体系

1. `DomainException`：领域层异常基类，实现 `HasErrorTraits` 接口，携带语义特征（`StandardErrorTrait`）
2. `ApplicationException`：应用层异常基类，携带明确的 `ErrorCodeLike` 错误码
3. `RemoteCallException`：HTTP Interface 调用下游服务失败时的统一异常，实现 `HasErrorTraits` 接口

## 各层异常处理规范

1. 领域层（Domain）：继承 `DomainException`，携带 `StandardErrorTrait` 语义特征，禁止依赖框架异常
2. 应用层（Application）：领域异常（`DomainException`）携带语义特征应**直接传播**，由 `DefaultErrorResolutionEngine` 自动映射为 HTTP 状态码；仅对**意外异常**（`RuntimeException`、`Exception`）使用 `ApplicationException` 包装，携带明确的 `ErrorCodeLike` 业务错误码
3. Boot 层（Composition Root）：通过 `ErrorMappingContributor` SPI 映射服务级异常（领域异常 → HTTP 错误码）；通用数据层异常由 `JpaErrorMappingContributor`（starter-jpa）统一处理
4. 适配器层（Adapter）：捕获 `RemoteCallException`，基于 `ErrorTrait` 语义特征转换为领域异常

## 错误码格式

格式：`{SERVICE}-{0xxx}`，SERVICE 为服务前缀（如 INGEST、REG、CATALOG），0xxx 映射到 HTTP 状态码
常用映射：0404（Not Found）、0409（Conflict）、0422（Unprocessable Entity）、0500（Internal Error）

## HTTP Interface 错误处理

1. 捕获 `RemoteCallException` 后，优先使用 `ex.getErrorTraits()` 基于语义特征判断
2. 备选方案：使用 `RemoteErrorHelper` 工具类基于 HTTP 状态码判断
3. 禁止直接捕获 `RestClientException`，必须使用 `RemoteCallException` 统一处理
