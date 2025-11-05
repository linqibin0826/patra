/**
 * 通用工具类包 - 跨服务共享的工具方法。
 *
 * <p>本包提供 Patra 平台所有微服务共享的通用工具类,包括哈希计算、字符串处理等常用功能。
 * 工具类遵循静态方法设计,不包含状态,线程安全。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>提供哈希计算工具(SHA-256、MD5 等)
 *   <li>提供字符串处理工具
 *   <li>提供日期时间处理工具
 *   <li>提供集合操作工具
 *   <li>补充 Hutool 和 JDK 标准库的功能
 * </ul>
 *
 * <h2>核心工具类</h2>
 *
 * <ul>
 *   <li>{@link com.patra.common.util.HashUtils} - 哈希计算工具,
 *       支持 SHA-256、MD5 等算法,用于内容去重和签名验证
 * </ul>
 *
 * <h2>HashUtils 核心功能</h2>
 *
 * <ul>
 *   <li><strong>SHA-256</strong>: 使用 SHA-256 算法计算字符串或字节数组的哈希值
 *   <li><strong>MD5</strong>: 使用 MD5 算法计算哈希值(用于非安全场景)
 *   <li><strong>线程安全</strong>: 静态方法,无状态,线程安全
 *   <li><strong>性能优化</strong>: 使用缓冲区提高大数据处理性能
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><strong>内容去重</strong>: 使用 SHA-256 计算文献内容哈希,作为去重键
 *   <li><strong>签名验证</strong>: 使用 SHA-256 计算请求签名,验证数据完整性
 *   <li><strong>缓存键生成</strong>: 使用 MD5 或 SHA-256 生成缓存键
 *   <li><strong>数据完整性检查</strong>: 计算文件哈希,验证传输完整性
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 1. 计算字符串 SHA-256 哈希
 * String text = "Hello, Patra!";
 * String hash = HashUtils.sha256(text);
 * // 返回十六进制字符串,如: "a1b2c3d4..."
 *
 * // 2. 计算字节数组 SHA-256 哈希
 * byte[] data = "content".getBytes(StandardCharsets.UTF_8);
 * String hash2 = HashUtils.sha256(data);
 *
 * // 3. 计算 MD5 哈希(非安全场景)
 * String md5 = HashUtils.md5(text);
 *
 * // 4. 用于内容去重
 * JsonNormalizerResult result = JsonNormalizer.normalizeDefault(literatureData);
 * String contentHash = HashUtils.sha256(result.getHashMaterial());
 * // 使用 contentHash 作为去重键
 * }</pre>
 *
 * <h2>工具类设计原则</h2>
 *
 * <ul>
 *   <li><strong>静态方法</strong>: 所有工具方法都是静态方法,不需要实例化
 *   <li><strong>无状态</strong>: 工具类不包含状态,线程安全
 *   <li><strong>命名清晰</strong>: 方法名称清晰表达功能,易于理解
 *   <li><strong>异常处理</strong>: 将受检异常包装为运行时异常,简化调用
 *   <li><strong>性能优化</strong>: 使用高效算法和缓冲区,提高性能
 * </ul>
 *
 * <h2>与 Hutool 的关系</h2>
 *
 * <p>本包补充 Hutool 工具库的功能:
 *
 * <ul>
 *   <li><strong>Hutool</strong>: 提供丰富的日期、字符串、集合、加密等工具
 *   <li><strong>本包</strong>: 提供 Patra 项目特定的工具方法,补充 Hutool 未覆盖的功能
 *   <li><strong>协作</strong>: 优先使用 Hutool,本包提供项目特定或 Hutool 不支持的功能
 * </ul>
 *
 * <h2>扩展指南</h2>
 *
 * <p>添加新的工具类时,遵循以下规范:
 *
 * <ul>
 *   <li>工具类名称使用 {@code *Utils} 后缀
 *   <li>所有方法都是 {@code public static}
 *   <li>私有构造函数防止实例化: {@code private XxxUtils() {}}
 *   <li>完整的 Javadoc 文档,包括参数、返回值、异常说明
 *   <li>提供使用示例
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.common.util;
