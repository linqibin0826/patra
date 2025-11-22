/// Storage 基础设施层数据对象包。
/// 
/// 本包包含 patra-object-storage 服务的数据对象(Data Object, DO),作为六边形架构基础设施层的一部分。 数据对象是数据库表的 Java 映射,与
/// MyBatis-Plus 框架配合实现 ORM 功能。
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.objectstorage.infra.persistence.entity.FileMetadataDO} - 文件元数据数据对象,映射
///       storage_file_metadata 表
/// 
/// ## 数据对象职责
/// 
/// - **数据库映射**: 通过注解映射到数据库表和字段
///   - **ORM 支持**: 与 MyBatis-Plus 配合实现 CRUD 操作
///   - **持久化载体**: 作为仓储层与数据库交互的数据载体
///   - **审计字段**: 包含创建人、更新人、时间戳、版本号等审计信息
///   - **软删除支持**: 通过 deleted 字段实现逻辑删除
/// 
/// ## 设计原则
/// 
/// - **技术实现**: 数据对象是技术实现细节,不包含业务逻辑
///   - **可变对象**: 使用可变字段和 setter 方法,便于 ORM 框架操作
///   - **框架注解**: 使用 MyBatis-Plus 注解(@TableName、@TableId、@TableField)
///   - **字段完整**: 包含数据库表的所有字段,确保数据完整性
///   - **命名规范**: 类名以 DO 结尾,字段名使用驼峰命名,通过注解映射数据库下划线命名
/// 
/// ## FileMetadataDO - 文件元数据数据对象
/// 
/// 映射数据库表 `storage_file_metadata`,包含以下字段分组:
/// 
/// - **主键标识**:
///       
/// - `id` - 主键,BIGINT UNSIGNED AUTO_INCREMENT
/// 
///   - **存储定位**:
///       
/// - `storageKey` - 完整存储键(bucket/objectKey),VARCHAR(768),唯一索引
///         - `bucketName` - 存储桶名称,VARCHAR(128)
///         - `objectKey` - 对象键,VARCHAR(512)
/// 
///   - **文件属性**:
///       
/// - `fileSize` - 文件大小(字节),BIGINT
///         - `contentType` - MIME 类型,VARCHAR(128)
///         - `md5Hash` - MD5 校验和,VARCHAR(64)
///         - `sha256Hash` - SHA-256 校验和,VARCHAR(128)
/// 
///   - **业务上下文**:
///       
/// - `serviceName` - 调用服务名称,VARCHAR(64)
///         - `businessType` - 业务分类,VARCHAR(64)
///         - `businessId` - 业务标识,VARCHAR(128)
///         - `correlationData` - 关联元数据,JSON
/// 
///   - **生命周期**:
///       
/// - `providerType` - 存储提供商类型,VARCHAR(32),枚举值
///         - `status` - 文件状态,VARCHAR(32),枚举值
///         - `uploadedAt` - 上传完成时间,TIMESTAMP(6)
///         - `expiresAt` - 过期时间,TIMESTAMP(6)
///         - `deletedAt` - 软删除时间,TIMESTAMP(6)
/// 
///   - **审计信息**:
///       
/// - `recordRemarks` - 审计备注,JSON
///         - `version` - 乐观锁版本号,BIGINT UNSIGNED
///         - `ipAddress` - 请求者 IP,VARBINARY(16)
///         - `createdAt` - 创建时间,TIMESTAMP(6)
///         - `createdBy` - 创建人 ID,BIGINT UNSIGNED
///         - `createdByName` - 创建人姓名,VARCHAR(100)
///         - `updatedAt` - 更新时间,TIMESTAMP(6)
///         - `updatedBy` - 更新人 ID,BIGINT UNSIGNED
///         - `updatedByName` - 更新人姓名,VARCHAR(100)
///         - `deleted` - 软删除标志,TINYINT(1)
/// 
/// ## MyBatis-Plus 注解
/// 
/// ```java
/// @Data
/// @TableName("storage_file_metadata")  // 映射表名
/// public class FileMetadataDO {
/// 
///     @TableId(value = "id", type = IdType.AUTO)  // 主键自增
///     private Long id;
/// 
///     @TableField("storage_key")  // 映射字段名
///     private String storageKey;
/// 
///     @TableField("bucket_name")
///     private String bucketName;
/// 
///     @TableField("object_key")
///     private String objectKey;
/// 
///     @TableField("file_size")
///     private Long fileSize;
/// 
///     // 枚举类型,MyBatis-Plus 自动转换为字符串
///     @TableField("provider_type")
///     private StorageProvider provider;
/// 
///     @TableField("file_status")
///     private FileStatus status;
/// 
///     // 乐观锁版本号
///     @Version
///     @TableField("version")
///     private Long version;
/// 
///     // 逻辑删除字段
///     @TableLogic
///     @TableField("deleted")
///     private Boolean deleted;
/// ```
/// 
/// ## 数据库表设计
/// 
/// ```java
/// CREATE TABLE storage_file_metadata (
///     id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
///     storage_key VARCHAR(768) NOT NULL COMMENT '完整存储键(bucket/objectKey)',
///     bucket_name VARCHAR(128) NOT NULL COMMENT '存储桶名称',
///     object_key VARCHAR(512) NOT NULL COMMENT '对象键',
///     file_size BIGINT NOT NULL COMMENT '文件大小(字节)',
///     content_type VARCHAR(128) COMMENT 'MIME 类型',
///     md5_hash VARCHAR(64) NOT NULL COMMENT 'MD5 校验和',
///     sha256_hash VARCHAR(128) COMMENT 'SHA-256 校验和',
///     service_name VARCHAR(64) NOT NULL COMMENT '调用服务名称',
///     business_type VARCHAR(64) NOT NULL COMMENT '业务分类',
///     business_id VARCHAR(128) NOT NULL COMMENT '业务标识',
///     correlation_data JSON COMMENT '关联元数据',
///     provider_type VARCHAR(32) NOT NULL COMMENT '存储提供商类型',
///     file_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '文件状态',
///     uploaded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '上传时间',
///     expires_at TIMESTAMP(6) COMMENT '过期时间',
///     deleted_at TIMESTAMP(6) COMMENT '删除时间',
///     record_remarks JSON COMMENT '审计备注',
///     version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
///     ip_address VARBINARY(16) COMMENT '请求者 IP',
///     created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
///     created_by BIGINT UNSIGNED COMMENT '创建人 ID',
///     created_by_name VARCHAR(100) COMMENT '创建人姓名',
///     updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
///     updated_by BIGINT UNSIGNED COMMENT '更新人 ID',
///     updated_by_name VARCHAR(100) COMMENT '更新人姓名',
///     deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标志',
/// 
///     UNIQUE KEY uk_storage_key (storage_key) COMMENT '存储键唯一索引',
///     KEY idx_uploaded_at (uploaded_at) COMMENT '上传时间索引',
///     KEY idx_deleted (deleted) COMMENT '软删除标志索引'
/// ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据表';
/// ```
/// 
/// ## 数据对象 vs 领域对象
/// 
/// <table border="1">
///   <tr>
///     <th>特性</th>
///     <th>数据对象(DO)</th>
///     <th>领域对象(聚合根)</th>
///   </tr>
///   <tr>
///     <td>用途</td>
///     <td>数据库映射</td>
///     <td>业务逻辑</td>
///   </tr>
///   <tr>
///     <td>可变性</td>
///     <td>可变(setter 方法)</td>
///     <td>封装变更(业务方法)</td>
///   </tr>
///   <tr>
///     <td>框架依赖</td>
///     <td>依赖 MyBatis-Plus</td>
///     <td>纯 Java,无框架依赖</td>
///   </tr>
///   <tr>
///     <td>业务逻辑</td>
///     <td>无</td>
///     <td>包含业务规则和验证</td>
///   </tr>
///   <tr>
///     <td>字段类型</td>
///     <td>基本类型和字符串</td>
///     <td>值对象和枚举</td>
///   </tr>
/// </table>
/// 
/// ## 转换示例
/// 
/// ```java
/// // 领域对象 → 数据对象
/// FileMetadata aggregate = FileMetadata.create(...);
/// FileMetadataDO dataObject = converter.toDO(aggregate);
/// mapper.insert(dataObject);
/// 
/// // 数据对象 → 领域对象
/// FileMetadataDO dataObject = mapper.selectById(123456L);
/// FileMetadata aggregate = converter.toAggregate(dataObject);
/// ```
/// 
/// ## 相关文档
/// 
/// - 聚合根: {@link com.patra.objectstorage.domain.model.aggregate.FileMetadata}
///   - 转换器: {@link com.patra.objectstorage.infra.persistence.converter.FileMetadataConverter}
///   - Mapper: {@link com.patra.objectstorage.infra.persistence.mapper.FileMetadataMapper}
///   - MyBatis-Plus 官方文档: <a href="https://baomidou.com/">baomidou.com</a>
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.objectstorage.infra.persistence.entity;
