/**
 * 字典 API 数据传输对象包 - REST API 契约层。
 *
 * <p>本包包含字典服务相关的 REST API 响应 DTOs,定义了字典类型、字典项和字典引用的数据传输对象。
 * 字典服务提供系统级的枚举和配置项管理,供其他服务查询和验证。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义字典类型的 API 响应格式
 *   <li>定义字典项的 API 响应格式
 *   <li>定义字典引用的请求格式
 *   <li>支持字典的查询、验证和健康检查
 *   <li>提供 JSON 序列化支持
 * </ul>
 *
 * <h2>核心 DTOs</h2>
 *
 * <ul>
 *   <li>{@code DictionaryTypeResp} - 字典类型响应,描述字典类别的元数据
 *   <li>{@code DictionaryItemResp} - 字典项响应,描述具体的字典条目
 *   <li>{@code DictionaryReferenceReq} - 字典引用请求,用于查询或验证字典项
 *   <li>{@code DictionaryValidationResp} - 字典验证响应,返回验证结果
 *   <li>{@code DictionaryHealthResp} - 字典健康检查响应,返回字典服务状态
 * </ul>
 *
 * <h2>字典应用场景</h2>
 *
 * <p>字典在 Registry 服务中广泛应用于配置管理:
 *
 * <ul>
 *   <li><strong>重试策略代码</strong>: {@code retryAfterPolicyCode} - IGNORE/RESPECT/CLAMP
 *   <li><strong>生命周期状态</strong>: {@code lifecycleStatusCode} - PLANNING/ACTIVE/DEPRECATED
 *   <li><strong>操作类型</strong>: {@code operationType} - ALL/HARVEST/UPDATE/BACKFILL
 *   <li><strong>配置作用域</strong>: {@code configScope} - SOURCE/OPERATION/TASK
 * </ul>
 *
 * <h2>DTO 设计原则</h2>
 *
 * <ul>
 *   <li><strong>不可变性</strong>: 所有 DTOs 使用 {@code record} 实现
 *   <li><strong>扁平化</strong>: 简化嵌套结构,便于客户端使用
 *   <li><strong>命名规范</strong>: {@code *Resp} 表示响应,{@code *Req} 表示请求
 *   <li><strong>无业务逻辑</strong>: 纯数据传输对象
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 查询字典类型
 * DictionaryTypeResp type = client.getDictionaryType("RETRY_AFTER_POLICY");
 *
 * // 查询字典项
 * List<DictionaryItemResp> items = client.getDictionaryItems("RETRY_AFTER_POLICY");
 *
 * // 验证字典引用
 * DictionaryReferenceReq ref = new DictionaryReferenceReq("RETRY_AFTER_POLICY", "RESPECT");
 * DictionaryValidationResp validation = client.validateDictionary(ref);
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.api.dto.dict;
