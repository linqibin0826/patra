/// JSON 工具包 - JSON 规范化和 ObjectMapper 持有者。
/// 
/// 本包提供 Patra 平台的 JSON 处理核心工具,包括 JSON 规范化器、ObjectMapper 持有者、
/// 节点映射工具和时间类型强制转换。这些工具支持内容签名、去重键生成、缓存键标准化等场景。
/// 
/// ## 职责
/// 
/// - 提供 JSON 规范化工具,确保输出的确定性和稳定性
///   - 提供全局 ObjectMapper 持有者,避免重复实例化
///   - 提供 JsonNode 映射和转换工具
///   - 提供时间类型强制转换,支持多种时间格式解析
///   - 支持 Spring 容器 mapper 桥接
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.common.json.JsonNormalizer} - JSON 规范化工具, 将任意输入转换为确定性规范
///       JSON,支持键排序、数组处理、空值策略等
///   - {@link com.patra.common.json.JsonMapperHolder} - 全局 ObjectMapper 持有者, 提供非 Spring 环境下的共享
///       JSON 配置,支持 Spring 容器 mapper 桥接
///   - {@link com.patra.common.json.JsonNormalizerConfig} - JSON 规范化配置, 定义规范化行为(键排序、空值移除、类型强制转换等)
///   - {@link com.patra.common.json.JsonNormalizerResult} - JSON 规范化结果, 包含规范 JSON 字符串和哈希材料
///   - {@link com.patra.common.json.JsonNodeMappings} - JsonNode 映射工具, 提供 JsonNode 到 Java 对象的转换
///   - {@link com.patra.common.json.TemporalCoercion} - 时间类型强制转换, 支持多种时间格式解析(秒/毫秒 epoch、ISO-8601
///       等)
/// 
/// ## JsonNormalizer 核心功能
/// 
/// - **键排序**: 使用 ASCII/Unicode 比较器进行稳定的对象键排序
///   - **数组处理**: 按类型标签和序列化值对数组去重和排序,保留序列字段顺序
///   - **空值策略**: 移除空对象/数组/字符串,支持白名单
///   - **类型强制转换**: 规范化布尔值、数字和时间戳,去除 BigDecimal 尾随零
///   - **字符串清理**: trim、空白折叠、字段级小写
///   - **时间规范化**: 多格式解析(秒/毫秒 epoch),输出 UTC ISO-8601
///   - **安全防护**: UTF-8 字节限制、最大深度、拒绝非有限数字
/// 
/// ## 使用场景
/// 
/// - **内容签名**: 使用 {@link com.patra.common.json.JsonNormalizer} 生成规范 JSON, 然后使用
///       {@link com.patra.common.util.HashUtils} 计算签名
///   - **去重键生成**: 规范化出版物内容,生成稳定的哈希值作为去重键
///   - **缓存键标准化**: 规范化请求参数,生成一致的缓存键
///   - **多源数据规范化**: 统一不同数据源的 JSON 格式
///   - **ObjectMapper 共享**: 在非 Spring 环境下使用 {@link
///       com.patra.common.json.JsonMapperHolder}
/// 
/// ## 使用示例
/// 
/// ```java
/// // 1. 快速规范化(使用默认配置)
/// JsonNormalizerResult result = JsonNormalizer.normalizeDefault(payload);
/// String canonicalJson = result.getCanonicalJson();
/// byte[] hashMaterial = result.getHashMaterial();
/// 
/// // 2. 自定义配置规范化
/// JsonNormalizer normalizer = JsonNormalizer.withConfig(
///     JsonNormalizerConfig.builder()
///         .coerceNumber(true)
///         .coerceTime(true)
///         .removeEmpty(true)
///         .build()
/// );
/// JsonNormalizerResult result2 = normalizer.normalize(complexPayload);
/// 
/// // 3. 用于内容去重
/// JsonNormalizerResult result3 = JsonNormalizer.normalizeDefault(publicationData);
/// String contentHash = HashUtils.sha256(result3.getHashMaterial());
/// // 使用 contentHash 作为去重键
/// 
/// // 4. ObjectMapper 持有者(非 Spring 环境)
/// ObjectMapper om = JsonMapperHolder.getObjectMapper();
/// String json = om.writeValueAsString(data);
/// 
/// // 5. Spring 环境自动桥接(由 starter 完成)
/// JsonMapperHolder.register(containerManagedMapper);
/// ```
/// 
/// ## JsonMapperHolder 设计
/// 
/// {@link com.patra.common.json.JsonMapperHolder} 提供全局 ObjectMapper 访问:
/// 
/// - **非 Spring 环境**: 使用 `getObjectMapper()` 获取默认配置的 ObjectMapper
///   - **Spring 环境**: 启动器自动注册容器管理的 mapper,使用 `register()` 方法
///   - **线程安全**: ObjectMapper 是线程安全的,可以在多线程环境下共享
/// 
/// ## 时间规范化支持
/// 
/// {@link com.patra.common.json.TemporalCoercion} 支持多种时间格式解析:
/// 
/// - 秒级 Unix 时间戳(如 `1609459200`)
///   - 毫秒级 Unix 时间戳(如 `1609459200000`)
///   - ISO-8601 格式(如 `2021-01-01T00:00:00Z`)
///   - 自定义日期格式(通过配置)
/// 
/// 输出统一为 UTC ISO-8601 格式,毫秒精度: `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`
/// 
/// ## 设计原则
/// 
/// - **确定性**: 相同输入总是产生相同的规范化输出
///   - **框架无关**: 不依赖 Spring,可在任何环境使用
///   - **配置灵活**: 通过 {@link com.patra.common.json.JsonNormalizerConfig} 自定义行为
///   - **性能优化**: 使用缓冲区和高效算法,支持大数据处理
///   - **安全防护**: 强制执行字节限制和深度限制,防止 DoS 攻击
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.common.json;
