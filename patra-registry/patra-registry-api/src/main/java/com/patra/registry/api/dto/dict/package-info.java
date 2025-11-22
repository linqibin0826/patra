/// 字典 API 数据传输对象包 - REST API 契约层。
/// 
/// 本包包含字典服务相关的 REST API 响应 DTOs,定义了字典类型、字典项和字典引用的数据传输对象。 字典服务提供系统级的枚举和配置项管理,供其他服务查询和验证。
/// 
/// ## 职责
/// 
/// - 定义字典类型的 API 响应格式
///   - 定义字典项的 API 响应格式
///   - 定义字典引用的请求格式
///   - 支持字典的查询、验证和健康检查
///   - 提供 JSON 序列化支持
/// 
/// ## 核心 DTOs
/// 
/// - `DictionaryTypeResp` - 字典类型响应,描述字典类别的元数据
///   - `DictionaryItemResp` - 字典项响应,描述具体的字典条目
///   - `DictionaryReferenceReq` - 字典引用请求,用于查询或验证字典项
///   - `DictionaryValidationResp` - 字典验证响应,返回验证结果
///   - `DictionaryHealthResp` - 字典健康检查响应,返回字典服务状态
/// 
/// ## 字典应用场景
/// 
/// 字典在 Registry 服务中广泛应用于配置管理:
/// 
/// - **重试策略代码**: `retryAfterPolicyCode` - IGNORE/RESPECT/CLAMP
///   - **生命周期状态**: `lifecycleStatusCode` - PLANNING/ACTIVE/DEPRECATED
///   - **操作类型**: `operationType` - ALL/HARVEST/UPDATE/BACKFILL
///   - **配置作用域**: `configScope` - SOURCE/OPERATION/TASK
/// 
/// ## DTO 设计原则
/// 
/// - **不可变性**: 所有 DTOs 使用 `record` 实现
///   - **扁平化**: 简化嵌套结构,便于客户端使用
///   - **命名规范**: `*Resp` 表示响应,`*Req` 表示请求
///   - **无业务逻辑**: 纯数据传输对象
/// 
/// ## 使用示例
/// 
/// ```java
/// // 查询字典类型
/// DictionaryTypeResp type = client.getDictionaryType("RETRY_AFTER_POLICY");
/// 
/// // 查询字典项
/// List<DictionaryItemResp> items = client.getDictionaryItems("RETRY_AFTER_POLICY");
/// 
/// // 验证字典引用
/// DictionaryReferenceReq ref = new DictionaryReferenceReq("RETRY_AFTER_POLICY", "RESPECT");
/// DictionaryValidationResp validation = client.validateDictionary(ref);
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.api.dto.dict;
