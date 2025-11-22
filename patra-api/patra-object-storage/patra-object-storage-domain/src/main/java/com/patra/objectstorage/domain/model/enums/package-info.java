/// Storage 领域模型枚举类型包。
/// 
/// 本包包含 patra-object-storage 服务的枚举类型,作为六边形架构领域层的一部分。 枚举类型表示有限的固定值集合,提供类型安全、自文档化和编译期检查,是领域模型的重要组成部分。
/// 
/// ## 核心枚举
/// 
/// - {@link com.patra.objectstorage.domain.model.enums.FileStatus} - 文件状态枚举,表示文件的生命周期状态
///   - {@link com.patra.objectstorage.domain.model.enums.StorageProvider} - 存储提供商枚举,表示对象存储类型
/// 
/// ## 枚举特性
/// 
/// - **类型安全**: 编译期检查,避免使用魔法字符串或数字
///   - **有限值集**: 预定义的固定值,防止非法状态
///   - **自文档化**: 枚举名称清晰表达业务含义
///   - **行为封装**: 枚举可包含方法,封装状态转换逻辑
///   - **持久化友好**: 通过 `@Enumerated(EnumType.STRING)` 存储为字符串
/// 
/// ## 设计原则
/// 
/// - **纯 Java 实现**: 禁止依赖 Spring、MyBatis 等框架,保持领域层纯净
///   - **业务语义**: 枚举值名称体现业务概念,而非技术实现
///   - **稳定性**: 已发布枚举值不得删除,仅允许追加新值
///   - **可扩展性**: 提供工厂方法支持从字符串解析枚举值
/// 
/// ## FileStatus - 文件状态
/// 
/// 表示文件的生命周期状态:
/// 
/// - `ACTIVE` - 活跃状态,文件正常可用
///   - `DELETED` - 已删除状态,文件已软删除
/// 
/// **使用示例**:
/// 
/// ```java
/// // 创建聚合根时设置初始状态
/// FileMetadata metadata = FileMetadata.create(...);
/// assert metadata.getStatus() == FileStatus.ACTIVE;
/// 
/// // 软删除操作
/// metadata.markAsDeleted(1001L, "admin");
/// assert metadata.getStatus() == FileStatus.DELETED;
/// 
/// // 状态检查
/// if (metadata.getStatus() == FileStatus.DELETED) {
///     log.warn("文件已删除: {", metadata.getStorageKey().fullKey());
/// ```
/// 
/// ## StorageProvider - 存储提供商
/// 
/// 表示对象存储类型:
/// 
/// - `MINIO` - MinIO 对象存储
///   - `S3` - Amazon S3 或兼容 S3 的对象存储
///   - `ALIYUN_OSS` - 阿里云对象存储(未来扩展)
/// 
/// **使用示例**:
/// 
/// ```java
/// // 从字符串解析枚举值
/// StorageProvider provider = StorageProvider.fromName("MINIO");
/// assert provider == StorageProvider.MINIO;
/// 
/// // 获取枚举值名称
/// String name = StorageProvider.S3.name();  // "S3"
/// 
/// // 在聚合根中使用
/// FileMetadata metadata = FileMetadata.create(
///     storageKey, fileSize, checksum, context,
///     StorageProvider.MINIO  // 指定存储提供商
/// );
/// 
/// // 根据提供商类型执行不同逻辑
/// switch (metadata.getProvider()) {
///     case MINIO -> log.info("使用 MinIO 存储");
///     case S3 -> log.info("使用 S3 存储");
///     default -> throw new UnsupportedOperationException("不支持的存储类型");
/// ```
/// 
/// ## 枚举持久化
/// 
/// 枚举值在数据库中存储为字符串,确保可读性和稳定性:
/// 
/// ```java
/// // 实体类中的枚举字段
/// public class FileMetadataDO {
/// 
///     @TableField("file_status")
///     private FileStatus status;  // 存储为 "ACTIVE" 或 "DELETED"
/// 
///     @TableField("provider_type")
///     private StorageProvider provider;  // 存储为 "MINIO" 或 "S3"
/// ```
/// 
/// ## 枚举扩展
/// 
/// 添加新枚举值时遵循向后兼容原则:
/// 
/// ```java
/// // ✅ 正确:追加新值
/// public enum StorageProvider {
///     MINIO,
///     S3,
///     ALIYUN_OSS;  // 新增值
/// 
/// // ❌ 错误:删除已有值(破坏兼容性)
/// public enum StorageProvider {
///     // MINIO,  ❌ 删除会导致历史数据无法解析
///     S3,
///     ALIYUN_OSS;
/// ```
/// 
/// ## 枚举与字符串的对比
/// 
/// <table border="1">
///   <tr>
///     <th>特性</th>
///     <th>枚举</th>
///     <th>字符串</th>
///   </tr>
///   <tr>
///     <td>类型安全</td>
///     <td>✅ 编译期检查</td>
///     <td>❌ 运行时才能发现错误</td>
///   </tr>
///   <tr>
///     <td>值限制</td>
///     <td>✅ 有限值集</td>
///     <td>❌ 任意字符串</td>
///   </tr>
///   <tr>
///     <td>IDE 支持</td>
///     <td>✅ 自动补全、重构</td>
///     <td>❌ 无法自动补全</td>
///   </tr>
///   <tr>
///     <td>性能</td>
///     <td>✅ 常量池,高效</td>
///     <td>⚠️ 字符串比较开销</td>
///   </tr>
///   <tr>
///     <td>可读性</td>
///     <td>✅ 清晰表达业务含义</td>
///     <td>⚠️ 依赖命名规范</td>
///   </tr>
/// </table>
/// 
/// ## 相关文档
/// 
/// - 聚合根: {@link com.patra.objectstorage.domain.model.aggregate.FileMetadata}
///   - 值对象: {@link com.patra.objectstorage.domain.model.vo}
///   - Effective Java - Item 34: Use enums instead of int constants
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.objectstorage.domain.model.enums;
