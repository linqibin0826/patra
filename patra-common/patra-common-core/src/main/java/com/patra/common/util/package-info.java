/// 通用工具类包 - 跨服务共享的工具方法。
///
/// 本包提供 Patra 平台所有微服务共享的通用工具类,包括哈希计算、字符串处理等常用功能。 工具类遵循静态方法设计,不包含状态,线程安全。
///
/// ## 职责
///
/// - 提供哈希计算工具(SHA-256、MD5 等)
///   - 提供字符串处理工具
///   - 提供日期时间处理工具
///   - 提供集合操作工具
///   - 补充 Hutool 和 JDK 标准库的功能
///
/// ## 核心工具类
///
/// - {@link com.patra.common.util.HashUtils} - 哈希计算工具, 支持 SHA-256、MD5 等算法,用于内容去重和签名验证
///
/// ## HashUtils 核心功能
///
/// - **SHA-256**: 使用 SHA-256 算法计算字符串或字节数组的哈希值
///   - **MD5**: 使用 MD5 算法计算哈希值(用于非安全场景)
///   - **线程安全**: 静态方法,无状态,线程安全
///   - **性能优化**: 使用缓冲区提高大数据处理性能
///
/// ## 使用场景
///
/// - **内容去重**: 使用 SHA-256 计算文献内容哈希,作为去重键
///   - **签名验证**: 使用 SHA-256 计算请求签名,验证数据完整性
///   - **缓存键生成**: 使用 MD5 或 SHA-256 生成缓存键
///   - **数据完整性检查**: 计算文件哈希,验证传输完整性
///
/// ## 使用示例
///
/// ```java
/// // 1. 计算字符串 SHA-256 哈希
/// String text = "Hello, Patra!";
/// String hash = HashUtils.sha256(text);
/// // 返回十六进制字符串,如: "a1b2c3d4..."
///
/// // 2. 计算字节数组 SHA-256 哈希
/// byte[] data = "content".getBytes(StandardCharsets.UTF_8);
/// String hash2 = HashUtils.sha256(data);
///
/// // 3. 计算 MD5 哈希(非安全场景)
/// String md5 = HashUtils.md5(text);
///
/// // 4. 用于内容去重
/// JsonNormalizerResult result = JsonNormalizer.normalizeDefault(publicationData);
/// String contentHash = HashUtils.sha256(result.getHashMaterial());
/// // 使用 contentHash 作为去重键
/// ```
///
/// ## 工具类设计原则
///
/// - **静态方法**: 所有工具方法都是静态方法,不需要实例化
///   - **无状态**: 工具类不包含状态,线程安全
///   - **命名清晰**: 方法名称清晰表达功能,易于理解
///   - **异常处理**: 将受检异常包装为运行时异常,简化调用
///   - **性能优化**: 使用高效算法和缓冲区,提高性能
///
/// ## 与 Hutool 的关系
///
/// 本包补充 Hutool 工具库的功能:
///
/// - **Hutool**: 提供丰富的日期、字符串、集合、加密等工具
///   - **本包**: 提供 Patra 项目特定的工具方法,补充 Hutool 未覆盖的功能
///   - **协作**: 优先使用 Hutool,本包提供项目特定或 Hutool 不支持的功能
///
/// ## 扩展指南
///
/// 添加新的工具类时,遵循以下规范:
///
/// - 工具类名称使用 `*Utils` 后缀
///   - 所有方法都是 `public static`
///   - 私有构造函数防止实例化: `private XxxUtils() {`}
///   - 完整的 Javadoc 文档,包括参数、返回值、异常说明
///   - 提供使用示例
///
/// @since 0.1.0
/// @author linqibin
package com.patra.common.util;
