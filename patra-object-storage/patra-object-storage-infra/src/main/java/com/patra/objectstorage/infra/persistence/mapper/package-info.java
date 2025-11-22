/// Storage 基础设施层 MyBatis Mapper 包。
///
/// 本包包含 patra-object-storage 服务的 MyBatis Mapper 接口,作为六边形架构基础设施层的一部分。 Mapper 接口定义了数据库访问方法,与
// MyBatis-Plus
/// 框架配合实现 ORM 功能。
///
/// ## 核心组件
///
/// - {@link com.patra.objectstorage.infra.persistence.mapper.FileMetadataMapper} - 文件元数据 Mapper,继承
///       BaseMapper 提供基础 CRUD 操作,并扩展自定义查询方法
///
/// ## Mapper 职责
///
/// - **数据库访问**: 执行 SQL 查询、插入、更新、删除操作
///   - **基础 CRUD**: 通过继承 BaseMapper 获得开箱即用的 CRUD 方法
///   - **自定义查询**: 定义业务特定的查询方法(如 findByStorageKey)
///   - **索引优化**: 利用数据库索引优化查询性能
///   - **事务支持**: 参与 Spring 事务管理,确保数据一致性
///
/// ## 设计原则
///
/// - **继承 BaseMapper**: 继承 MyBatis-Plus 提供的 BaseMapper,复用基础方法
///   - **接口驱动**: 仅定义接口,MyBatis-Plus 自动生成实现
///   - **注解配置**: 使用 `@Mapper` 注解标记,Spring 自动扫描注册
///   - **SQL 隔离**: 复杂 SQL 写在 XML 文件中,简单查询使用注解
///   - **参数类型安全**: 方法参数使用强类型(如 String、Long),避免 Map
///
/// ## FileMetadataMapper - 文件元数据 Mapper
///
/// 文件元数据 Mapper 提供以下方法:
///
/// - **基础 CRUD(继承自 BaseMapper)**:
///
/// - `insert(FileMetadataDO)` - 插入新记录,返回影响行数
///         - `updateById(FileMetadataDO)` - 根据 ID 更新记录
///         - `selectById(Long)` - 根据 ID 查询记录
///         - `deleteById(Long)` - 根据 ID 删除记录(逻辑删除)
///         - `selectList(Wrapper)` - 条件查询列表
///         - `selectPage(Page, Wrapper)` - 分页查询
///
///   - **自定义查询**:
///
/// - `findByStorageKey(String)` - 根据存储键查询记录(唯一索引查询)
///
/// ## Mapper 接口定义
///
/// ```java
/// @Mapper
/// public interface FileMetadataMapper extends BaseMapper<FileMetadataDO> {
///
///     *      * 根据存储键查询文件元数据。
///      *
///      *
/// 使用唯一索引 uk_storage_key 查询,性能高效。
///      *
///      * @param storageKey 完整存储键(bucket/objectKey)
///      * @return 文件元数据数据对象,不存在则返回 null
///      *\/
///     @Select("SELECT * FROM storage_file_metadata WHERE storage_key = #{storageKey AND deleted =
// 0")
///     FileMetadataDO findByStorageKey(@Param("storageKey") String storageKey);
/// ```
///
/// ## BaseMapper 提供的方法
///
/// MyBatis-Plus 的 BaseMapper 接口提供以下常用方法:
///
/// ```java
/// // 插入操作
/// int insert(T entity);                        // 插入一条记录
///
/// // 删除操作
/// int deleteById(Serializable id);             // 根据 ID 删除
/// int deleteByMap(Map<String, Object> map);    // 根据 Map 条件删除
/// int delete(Wrapper<T> wrapper);              // 根据 Wrapper 条件删除
///
/// // 更新操作
/// int updateById(T entity);                    // 根据 ID 更新
/// int update(T entity, Wrapper<T> wrapper);    // 根据 Wrapper 条件更新
///
/// // 查询操作
/// T selectById(Serializable id);               // 根据 ID 查询
/// List<T> selectBatchIds(Collection<? extends Serializable> ids);  // 批量查询
/// List<T> selectByMap(Map<String, Object> map);                    // 根据 Map 查询
/// T selectOne(Wrapper<T> wrapper);             // 查询单条记录
/// Long selectCount(Wrapper<T> wrapper);        // 查询总数
/// List<T> selectList(Wrapper<T> wrapper);      // 查询列表
/// IPage<T> selectPage(IPage<T> page, Wrapper<T> wrapper);  // 分页查询
/// ```
///
/// ## 使用示例
///
/// **在仓储实现中使用 Mapper**:
///
/// ```java
/// @Repository
/// @RequiredArgsConstructor
/// public class FileMetadataRepositoryImpl implements FileMetadataRepository {
///
///     private final FileMetadataMapper mapper;  // 注入 Mapper
///     private final FileMetadataConverter converter;
///
///     @Override
///     public FileMetadata save(FileMetadata metadata) {
///         FileMetadataDO dataObject = converter.toDO(metadata);
///
///         // 使用 BaseMapper 提供的方法
///         if (metadata.getId() == null) {
///             mapper.insert(dataObject);  // 插入新记录 else {
///             mapper.updateById(dataObject);  // 更新现有记录
///
///         // 重新查询获取数据库生成的字段
///         FileMetadataDO persisted = mapper.selectById(dataObject.getId());
///         return converter.toAggregate(persisted);
///
///     @Override
///     public Optional<FileMetadata> findByStorageKey(StorageKey storageKey) {
///         // 使用自定义查询方法
///         FileMetadataDO dataObject = mapper.findByStorageKey(storageKey.fullKey());
///         return Optional.ofNullable(converter.toAggregate(dataObject));
/// ```
///
/// **自定义查询示例**:
///
/// ```java
/// // 1. 使用注解定义简单查询
/// @Select("SELECT * FROM storage_file_metadata WHERE bucket_name = #{bucket AND deleted = 0")
/// List<FileMetadataDO> findByBucket(@Param("bucket") String bucket);
///
/// // 2. 使用 Wrapper 构建动态查询
/// LambdaQueryWrapper<FileMetadataDO> wrapper = new LambdaQueryWrapper<>();
/// wrapper.eq(FileMetadataDO::getBucketName, "publication-files")
///        .ge(FileMetadataDO::getUploadedAt, startTime)
///        .le(FileMetadataDO::getUploadedAt, endTime)
///        .orderByDesc(FileMetadataDO::getUploadedAt);
/// List<FileMetadataDO> results = mapper.selectList(wrapper);
///
/// // 3. 分页查询
/// Page<FileMetadataDO> page = new Page<>(1, 10);  // 第 1 页,每页 10 条
/// LambdaQueryWrapper<FileMetadataDO> wrapper = new LambdaQueryWrapper<>();
/// wrapper.eq(FileMetadataDO::getServiceName, "patra-ingest");
/// IPage<FileMetadataDO> result = mapper.selectPage(page, wrapper);
/// ```
///
/// ## 索引优化
///
/// 自定义查询方法应利用数据库索引优化性能:
///
/// - **uk_storage_key**: 唯一索引,查询 storage_key 字段(最优性能)
///   - **idx_uploaded_at**: 普通索引,查询 uploaded_at 字段
///   - **idx_deleted**: 普通索引,过滤软删除记录
///
/// ```java
/// // ✅ 正确:使用唯一索引,性能高
/// FileMetadataDO result = mapper.findByStorageKey("bucket/key");
///
/// // ⚠️ 注意:全表扫描,性能低(如果没有索引)
/// LambdaQueryWrapper<FileMetadataDO> wrapper = new LambdaQueryWrapper<>();
/// wrapper.like(FileMetadataDO::getObjectKey, "article");  // LIKE 查询可能无法使用索引
/// List<FileMetadataDO> results = mapper.selectList(wrapper);
/// ```
///
/// ## 事务管理
///
/// Mapper 方法自动参与 Spring 事务:
///
/// ```java
/// @Service
/// @RequiredArgsConstructor
/// public class RecordUploadOrchestrator {
///
///     private final FileMetadataRepository repository;
///
///     @Transactional  // 事务边界
///     public RecordUploadResult execute(RecordUploadCommand command) {
///         // 1. 创建聚合根
///         FileMetadata metadata = FileMetadata.create(...);
///
///         // 2. 保存聚合根(Mapper 方法参与此事务)
///         FileMetadata saved = repository.save(metadata);
///
///         // 3. 其他数据库操作(同一事务)
///         // ...
///
///         return new RecordUploadResult(saved.getId(), saved.getUploadedAt());  // 事务提交或回滚
/// ```
///
/// ## MyBatis-Plus 配置
///
/// ```java
/// # application.yml
/// mybatis-plus:
///   configuration:
///     map-underscore-to-camel-case: true  # 自动映射下划线命名
///     log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl  # SQL 日志
///   global-config:
///     db-config:
///       logic-delete-field: deleted        # 逻辑删除字段
///       logic-delete-value: 1              # 删除标记值
///       logic-not-delete-value: 0          # 未删除标记值
///   type-handlers-package: com.patra.objectstorage.infra.persistence.typehandler  # 类型处理器
/// ```
///
/// ## 相关文档
///
/// - 数据对象: {@link com.patra.objectstorage.infra.persistence.entity.FileMetadataDO}
///   - 仓储实现: {@link
// com.patra.objectstorage.infra.persistence.repository.FileMetadataRepositoryImpl}
///   - MyBatis-Plus 官方文档: <a href="https://baomidou.com/">baomidou.com</a>
///
/// @author linqibin
/// @since 0.1.0
package com.patra.objectstorage.infra.persistence.mapper;
