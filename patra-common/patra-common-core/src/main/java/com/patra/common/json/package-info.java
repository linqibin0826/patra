/**
 * JSON 工具包 - JSON 规范化和 ObjectMapper 持有者。
 *
 * <p>本包提供 Patra 平台的 JSON 处理核心工具,包括 JSON 规范化器、ObjectMapper 持有者、
 * 节点映射工具和时间类型强制转换。这些工具支持内容签名、去重键生成、缓存键标准化等场景。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>提供 JSON 规范化工具,确保输出的确定性和稳定性
 *   <li>提供全局 ObjectMapper 持有者,避免重复实例化
 *   <li>提供 JsonNode 映射和转换工具
 *   <li>提供时间类型强制转换,支持多种时间格式解析
 *   <li>支持 Spring 容器 mapper 桥接
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.common.json.JsonNormalizer} - JSON 规范化工具, 将任意输入转换为确定性规范
 *       JSON,支持键排序、数组处理、空值策略等
 *   <li>{@link com.patra.common.json.JsonMapperHolder} - 全局 ObjectMapper 持有者, 提供非 Spring 环境下的共享
 *       JSON 配置,支持 Spring 容器 mapper 桥接
 *   <li>{@link com.patra.common.json.JsonNormalizerConfig} - JSON 规范化配置, 定义规范化行为(键排序、空值移除、类型强制转换等)
 *   <li>{@link com.patra.common.json.JsonNormalizerResult} - JSON 规范化结果, 包含规范 JSON 字符串和哈希材料
 *   <li>{@link com.patra.common.json.JsonNodeMappings} - JsonNode 映射工具, 提供 JsonNode 到 Java 对象的转换
 *   <li>{@link com.patra.common.json.TemporalCoercion} - 时间类型强制转换, 支持多种时间格式解析(秒/毫秒 epoch、ISO-8601
 *       等)
 * </ul>
 *
 * <h2>JsonNormalizer 核心功能</h2>
 *
 * <ul>
 *   <li><strong>键排序</strong>: 使用 ASCII/Unicode 比较器进行稳定的对象键排序
 *   <li><strong>数组处理</strong>: 按类型标签和序列化值对数组去重和排序,保留序列字段顺序
 *   <li><strong>空值策略</strong>: 移除空对象/数组/字符串,支持白名单
 *   <li><strong>类型强制转换</strong>: 规范化布尔值、数字和时间戳,去除 BigDecimal 尾随零
 *   <li><strong>字符串清理</strong>: trim、空白折叠、字段级小写
 *   <li><strong>时间规范化</strong>: 多格式解析(秒/毫秒 epoch),输出 UTC ISO-8601
 *   <li><strong>安全防护</strong>: UTF-8 字节限制、最大深度、拒绝非有限数字
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><strong>内容签名</strong>: 使用 {@link com.patra.common.json.JsonNormalizer} 生成规范 JSON, 然后使用
 *       {@link com.patra.common.util.HashUtils} 计算签名
 *   <li><strong>去重键生成</strong>: 规范化出版物内容,生成稳定的哈希值作为去重键
 *   <li><strong>缓存键标准化</strong>: 规范化请求参数,生成一致的缓存键
 *   <li><strong>多源数据规范化</strong>: 统一不同数据源的 JSON 格式
 *   <li><strong>ObjectMapper 共享</strong>: 在非 Spring 环境下使用 {@link
 *       com.patra.common.json.JsonMapperHolder}
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 1. 快速规范化(使用默认配置)
 * JsonNormalizerResult result = JsonNormalizer.normalizeDefault(payload);
 * String canonicalJson = result.getCanonicalJson();
 * byte[] hashMaterial = result.getHashMaterial();
 *
 * // 2. 自定义配置规范化
 * JsonNormalizer normalizer = JsonNormalizer.withConfig(
 *     JsonNormalizerConfig.builder()
 *         .coerceNumber(true)
 *         .coerceTime(true)
 *         .removeEmpty(true)
 *         .build()
 * );
 * JsonNormalizerResult result2 = normalizer.normalize(complexPayload);
 *
 * // 3. 用于内容去重
 * JsonNormalizerResult result3 = JsonNormalizer.normalizeDefault(publicationData);
 * String contentHash = HashUtils.sha256(result3.getHashMaterial());
 * // 使用 contentHash 作为去重键
 *
 * // 4. ObjectMapper 持有者(非 Spring 环境)
 * ObjectMapper om = JsonMapperHolder.getObjectMapper();
 * String json = om.writeValueAsString(data);
 *
 * // 5. Spring 环境自动桥接(由 starter 完成)
 * JsonMapperHolder.register(containerManagedMapper);
 * }</pre>
 *
 * <h2>JsonMapperHolder 设计</h2>
 *
 * <p>{@link com.patra.common.json.JsonMapperHolder} 提供全局 ObjectMapper 访问:
 *
 * <ul>
 *   <li><strong>非 Spring 环境</strong>: 使用 {@code getObjectMapper()} 获取默认配置的 ObjectMapper
 *   <li><strong>Spring 环境</strong>: 启动器自动注册容器管理的 mapper,使用 {@code register()} 方法
 *   <li><strong>线程安全</strong>: ObjectMapper 是线程安全的,可以在多线程环境下共享
 * </ul>
 *
 * <h2>时间规范化支持</h2>
 *
 * <p>{@link com.patra.common.json.TemporalCoercion} 支持多种时间格式解析:
 *
 * <ul>
 *   <li>秒级 Unix 时间戳(如 {@code 1609459200})
 *   <li>毫秒级 Unix 时间戳(如 {@code 1609459200000})
 *   <li>ISO-8601 格式(如 {@code 2021-01-01T00:00:00Z})
 *   <li>自定义日期格式(通过配置)
 * </ul>
 *
 * <p>输出统一为 UTC ISO-8601 格式,毫秒精度: {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>确定性</strong>: 相同输入总是产生相同的规范化输出
 *   <li><strong>框架无关</strong>: 不依赖 Spring,可在任何环境使用
 *   <li><strong>配置灵活</strong>: 通过 {@link com.patra.common.json.JsonNormalizerConfig} 自定义行为
 *   <li><strong>性能优化</strong>: 使用缓冲区和高效算法,支持大数据处理
 *   <li><strong>安全防护</strong>: 强制执行字节限制和深度限制,防止 DoS 攻击
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.common.json;
