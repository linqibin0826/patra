/**
 * Storage 领域模型值对象包。
 *
 * <p>本包包含 patra-object-storage 服务的值对象(Value Object),作为六边形架构领域层的重要组成部分。
 * 值对象封装了领域概念,提供不可变性、自我验证和业务语义,是构建健壮领域模型的基石。
 *
 * <h2>核心值对象</h2>
 *
 * <ul>
 *   <li>{@link com.patra.objectstorage.domain.model.vo.StorageKey} - 不可变的存储定位符,包含 bucket 和 objectKey
 *   <li>{@link com.patra.objectstorage.domain.model.vo.FileSize} - 文件大小封装,单位字节
 *   <li>{@link com.patra.objectstorage.domain.model.vo.FileChecksum} - 校验和封装,包含 md5Hash 和 sha256Hash
 *   <li>{@link com.patra.objectstorage.domain.model.vo.BusinessContext} - 业务上下文封装,包含服务名、业务类型、业务标识、关联数据
 * </ul>
 *
 * <h2>值对象特性</h2>
 *
 * <ul>
 *   <li><strong>不可变性</strong>: 使用 Java Record 实现,创建后状态不可变
 *   <li><strong>值相等</strong>: 基于属性值判断相等性,而非对象引用
 *   <li><strong>自我验证</strong>: 在构造时验证业务规则,抛出 IllegalArgumentException
 *   <li><strong>无标识符</strong>: 值对象没有唯一标识符,仅通过属性值区分
 *   <li><strong>可替换性</strong>: 相同属性的值对象可以互相替换
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>纯 Java 实现</strong>: 禁止依赖 Spring、MyBatis 等框架,保持领域层纯净
 *   <li><strong>业务语义</strong>: 值对象名称和方法体现业务概念,而非技术实现
 *   <li><strong>封装复杂性</strong>: 将复杂的验证逻辑和计算封装在值对象内部
 *   <li><strong>防御性拷贝</strong>: 对可变类型(如 Map)执行防御性拷贝,确保不可变性
 * </ul>
 *
 * <h2>值对象使用示例</h2>
 *
 * <strong>StorageKey - 存储定位符</strong>:
 *
 * <pre>{@code
 * // 创建存储键
 * StorageKey key = new StorageKey("publication-files", "2024/01/article.pdf");
 *
 * // 获取完整键(用于数据库唯一约束)
 * String fullKey = key.fullKey();  // "publication-files/2024/01/article.pdf"
 *
 * // 匹配检查
 * boolean matches = key.matches("publication-files", "2024/01/article.pdf");  // true
 *
 * // 值对象相等性
 * StorageKey key2 = new StorageKey("publication-files", "2024/01/article.pdf");
 * assert key.equals(key2);  // true,基于属性值相等
 * }</pre>
 *
 * <strong>FileSize - 文件大小</strong>:
 *
 * <pre>{@code
 * // 创建文件大小
 * FileSize size = new FileSize(1024000L);  // 1MB
 *
 * // 获取字节数
 * long bytes = size.bytes();  // 1024000
 * }</pre>
 *
 * <strong>FileChecksum - 文件校验和</strong>:
 *
 * <pre>{@code
 * // 仅 MD5 校验和
 * FileChecksum checksum1 = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);
 *
 * // 包含 MD5 和 SHA-256
 * FileChecksum checksum2 = new FileChecksum(
 *     "5d41402abc4b2a76b9719d911017c592",
 *     "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae"
 * );
 *
 * // 获取校验和
 * String md5 = checksum2.md5Hash();
 * String sha256 = checksum2.sha256Hash();
 * }</pre>
 *
 * <strong>BusinessContext - 业务上下文</strong>:
 *
 * <pre>{@code
 * // 创建业务上下文
 * BusinessContext context = new BusinessContext(
 *     "patra-ingest",                          // serviceName
 *     "publication_batch",                      // businessType
 *     "batch-2024-01-15-001",                  // businessId
 *     Map.of(                                  // correlationData
 *         "sourceId", "pubmed",
 *         "pmcId", "PMC12345678"
 *     )
 * );
 *
 * // 获取上下文信息
 * String service = context.serviceName();       // "patra-ingest"
 * String type = context.businessType();         // "publication_batch"
 * String id = context.businessId();             // "batch-2024-01-15-001"
 * Map<String, Object> data = context.correlationData();  // 防御性拷贝,不可变
 * }</pre>
 *
 * <h2>值对象验证</h2>
 *
 * 所有值对象在构造时执行验证,无效输入抛出 {@code IllegalArgumentException}:
 *
 * <pre>{@code
 * // 无效输入示例
 * try {
 *     new StorageKey(null, "key");  // ❌ 抛出异常: 存储桶不能为空
 * } catch (IllegalArgumentException e) {
 *     log.error("创建 StorageKey 失败: {}", e.getMessage());
 * }
 *
 * try {
 *     new StorageKey("bucket", "");  // ❌ 抛出异常: 对象键不能为空
 * } catch (IllegalArgumentException e) {
 *     log.error("创建 StorageKey 失败: {}", e.getMessage());
 * }
 * }</pre>
 *
 * <h2>值对象与实体的区别</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>特性</th>
 *     <th>值对象</th>
 *     <th>实体(聚合根)</th>
 *   </tr>
 *   <tr>
 *     <td>标识符</td>
 *     <td>无</td>
 *     <td>有(id)</td>
 *   </tr>
 *   <tr>
 *     <td>可变性</td>
 *     <td>不可变</td>
 *     <td>可变</td>
 *   </tr>
 *   <tr>
 *     <td>相等性</td>
 *     <td>基于属性值</td>
 *     <td>基于标识符</td>
 *   </tr>
 *   <tr>
 *     <td>生命周期</td>
 *     <td>短暂,随实体创建/销毁</td>
 *     <td>长期,独立存在</td>
 *   </tr>
 *   <tr>
 *     <td>示例</td>
 *     <td>StorageKey, FileSize</td>
 *     <td>FileMetadata</td>
 *   </tr>
 * </table>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>聚合根: {@link com.patra.objectstorage.domain.model.aggregate.FileMetadata}
 *   <li>枚举类型: {@link com.patra.objectstorage.domain.model.enums}
 *   <li>DDD 值对象模式: <a
 *       href="https://martinfowler.com/bliki/ValueObject.html">martinfowler.com/bliki/ValueObject.html</a>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.objectstorage.domain.model.vo;
