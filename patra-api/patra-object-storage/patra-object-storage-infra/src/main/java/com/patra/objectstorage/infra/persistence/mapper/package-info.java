/**
 * Storage 基础设施层 MyBatis Mapper 包。
 *
 * <p>本包包含 patra-object-storage 服务的 MyBatis Mapper 接口,作为六边形架构基础设施层的一部分。 Mapper 接口定义了数据库访问方法,与 MyBatis-Plus
 * 框架配合实现 ORM 功能。
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.objectstorage.infra.persistence.mapper.FileMetadataMapper} - 文件元数据 Mapper,继承
 *       BaseMapper 提供基础 CRUD 操作,并扩展自定义查询方法
 * </ul>
 *
 * <h2>Mapper 职责</h2>
 *
 * <ul>
 *   <li><strong>数据库访问</strong>: 执行 SQL 查询、插入、更新、删除操作
 *   <li><strong>基础 CRUD</strong>: 通过继承 BaseMapper 获得开箱即用的 CRUD 方法
 *   <li><strong>自定义查询</strong>: 定义业务特定的查询方法(如 findByStorageKey)
 *   <li><strong>索引优化</strong>: 利用数据库索引优化查询性能
 *   <li><strong>事务支持</strong>: 参与 Spring 事务管理,确保数据一致性
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>继承 BaseMapper</strong>: 继承 MyBatis-Plus 提供的 BaseMapper,复用基础方法
 *   <li><strong>接口驱动</strong>: 仅定义接口,MyBatis-Plus 自动生成实现
 *   <li><strong>注解配置</strong>: 使用 {@code @Mapper} 注解标记,Spring 自动扫描注册
 *   <li><strong>SQL 隔离</strong>: 复杂 SQL 写在 XML 文件中,简单查询使用注解
 *   <li><strong>参数类型安全</strong>: 方法参数使用强类型(如 String、Long),避免 Map
 * </ul>
 *
 * <h2>FileMetadataMapper - 文件元数据 Mapper</h2>
 *
 * 文件元数据 Mapper 提供以下方法:
 *
 * <ul>
 *   <li><strong>基础 CRUD(继承自 BaseMapper)</strong>:
 *       <ul>
 *         <li>{@code insert(FileMetadataDO)} - 插入新记录,返回影响行数
 *         <li>{@code updateById(FileMetadataDO)} - 根据 ID 更新记录
 *         <li>{@code selectById(Long)} - 根据 ID 查询记录
 *         <li>{@code deleteById(Long)} - 根据 ID 删除记录(逻辑删除)
 *         <li>{@code selectList(Wrapper)} - 条件查询列表
 *         <li>{@code selectPage(Page, Wrapper)} - 分页查询
 *       </ul>
 *   <li><strong>自定义查询</strong>:
 *       <ul>
 *         <li>{@code findByStorageKey(String)} - 根据存储键查询记录(唯一索引查询)
 *       </ul>
 * </ul>
 *
 * <h2>Mapper 接口定义</h2>
 *
 * <pre>{@code
 * @Mapper
 * public interface FileMetadataMapper extends BaseMapper<FileMetadataDO> {
 *
 *     /**
 *      * 根据存储键查询文件元数据。
 *      *
 *      * <p>使用唯一索引 uk_storage_key 查询,性能高效。
 *      *
 *      * @param storageKey 完整存储键(bucket/objectKey)
 *      * @return 文件元数据数据对象,不存在则返回 null
 *      *\/
 *     @Select("SELECT * FROM storage_file_metadata WHERE storage_key = #{storageKey} AND deleted = 0")
 *     FileMetadataDO findByStorageKey(@Param("storageKey") String storageKey);
 * }
 * }</pre>
 *
 * <h2>BaseMapper 提供的方法</h2>
 *
 * MyBatis-Plus 的 BaseMapper 接口提供以下常用方法:
 *
 * <pre>{@code
 * // 插入操作
 * int insert(T entity);                        // 插入一条记录
 *
 * // 删除操作
 * int deleteById(Serializable id);             // 根据 ID 删除
 * int deleteByMap(Map<String, Object> map);    // 根据 Map 条件删除
 * int delete(Wrapper<T> wrapper);              // 根据 Wrapper 条件删除
 *
 * // 更新操作
 * int updateById(T entity);                    // 根据 ID 更新
 * int update(T entity, Wrapper<T> wrapper);    // 根据 Wrapper 条件更新
 *
 * // 查询操作
 * T selectById(Serializable id);               // 根据 ID 查询
 * List<T> selectBatchIds(Collection<? extends Serializable> ids);  // 批量查询
 * List<T> selectByMap(Map<String, Object> map);                    // 根据 Map 查询
 * T selectOne(Wrapper<T> wrapper);             // 查询单条记录
 * Long selectCount(Wrapper<T> wrapper);        // 查询总数
 * List<T> selectList(Wrapper<T> wrapper);      // 查询列表
 * IPage<T> selectPage(IPage<T> page, Wrapper<T> wrapper);  // 分页查询
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <strong>在仓储实现中使用 Mapper</strong>:
 *
 * <pre>{@code
 * @Repository
 * @RequiredArgsConstructor
 * public class FileMetadataRepositoryImpl implements FileMetadataRepository {
 *
 *     private final FileMetadataMapper mapper;  // 注入 Mapper
 *     private final FileMetadataConverter converter;
 *
 *     @Override
 *     public FileMetadata save(FileMetadata metadata) {
 *         FileMetadataDO dataObject = converter.toDO(metadata);
 *
 *         // 使用 BaseMapper 提供的方法
 *         if (metadata.getId() == null) {
 *             mapper.insert(dataObject);  // 插入新记录
 *         } else {
 *             mapper.updateById(dataObject);  // 更新现有记录
 *         }
 *
 *         // 重新查询获取数据库生成的字段
 *         FileMetadataDO persisted = mapper.selectById(dataObject.getId());
 *         return converter.toAggregate(persisted);
 *     }
 *
 *     @Override
 *     public Optional<FileMetadata> findByStorageKey(StorageKey storageKey) {
 *         // 使用自定义查询方法
 *         FileMetadataDO dataObject = mapper.findByStorageKey(storageKey.fullKey());
 *         return Optional.ofNullable(converter.toAggregate(dataObject));
 *     }
 * }
 * }</pre>
 *
 * <strong>自定义查询示例</strong>:
 *
 * <pre>{@code
 * // 1. 使用注解定义简单查询
 * @Select("SELECT * FROM storage_file_metadata WHERE bucket_name = #{bucket} AND deleted = 0")
 * List<FileMetadataDO> findByBucket(@Param("bucket") String bucket);
 *
 * // 2. 使用 Wrapper 构建动态查询
 * LambdaQueryWrapper<FileMetadataDO> wrapper = new LambdaQueryWrapper<>();
 * wrapper.eq(FileMetadataDO::getBucketName, "publication-files")
 *        .ge(FileMetadataDO::getUploadedAt, startTime)
 *        .le(FileMetadataDO::getUploadedAt, endTime)
 *        .orderByDesc(FileMetadataDO::getUploadedAt);
 * List<FileMetadataDO> results = mapper.selectList(wrapper);
 *
 * // 3. 分页查询
 * Page<FileMetadataDO> page = new Page<>(1, 10);  // 第 1 页,每页 10 条
 * LambdaQueryWrapper<FileMetadataDO> wrapper = new LambdaQueryWrapper<>();
 * wrapper.eq(FileMetadataDO::getServiceName, "patra-ingest");
 * IPage<FileMetadataDO> result = mapper.selectPage(page, wrapper);
 * }</pre>
 *
 * <h2>索引优化</h2>
 *
 * 自定义查询方法应利用数据库索引优化性能:
 *
 * <ul>
 *   <li><strong>uk_storage_key</strong>: 唯一索引,查询 storage_key 字段(最优性能)
 *   <li><strong>idx_uploaded_at</strong>: 普通索引,查询 uploaded_at 字段
 *   <li><strong>idx_deleted</strong>: 普通索引,过滤软删除记录
 * </ul>
 *
 * <pre>{@code
 * // ✅ 正确:使用唯一索引,性能高
 * FileMetadataDO result = mapper.findByStorageKey("bucket/key");
 *
 * // ⚠️ 注意:全表扫描,性能低(如果没有索引)
 * LambdaQueryWrapper<FileMetadataDO> wrapper = new LambdaQueryWrapper<>();
 * wrapper.like(FileMetadataDO::getObjectKey, "article");  // LIKE 查询可能无法使用索引
 * List<FileMetadataDO> results = mapper.selectList(wrapper);
 * }</pre>
 *
 * <h2>事务管理</h2>
 *
 * Mapper 方法自动参与 Spring 事务:
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class RecordUploadOrchestrator {
 *
 *     private final FileMetadataRepository repository;
 *
 *     @Transactional  // 事务边界
 *     public RecordUploadResult execute(RecordUploadCommand command) {
 *         // 1. 创建聚合根
 *         FileMetadata metadata = FileMetadata.create(...);
 *
 *         // 2. 保存聚合根(Mapper 方法参与此事务)
 *         FileMetadata saved = repository.save(metadata);
 *
 *         // 3. 其他数据库操作(同一事务)
 *         // ...
 *
 *         return new RecordUploadResult(saved.getId(), saved.getUploadedAt());
 *     }  // 事务提交或回滚
 * }
 * }</pre>
 *
 * <h2>MyBatis-Plus 配置</h2>
 *
 * <pre>{@code
 * # application.yml
 * mybatis-plus:
 *   configuration:
 *     map-underscore-to-camel-case: true  # 自动映射下划线命名
 *     log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl  # SQL 日志
 *   global-config:
 *     db-config:
 *       logic-delete-field: deleted        # 逻辑删除字段
 *       logic-delete-value: 1              # 删除标记值
 *       logic-not-delete-value: 0          # 未删除标记值
 *   type-handlers-package: com.patra.objectstorage.infra.persistence.typehandler  # 类型处理器
 * }</pre>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>数据对象: {@link com.patra.objectstorage.infra.persistence.entity.FileMetadataDO}
 *   <li>仓储实现: {@link com.patra.objectstorage.infra.persistence.repository.FileMetadataRepositoryImpl}
 *   <li>MyBatis-Plus 官方文档: <a href="https://baomidou.com/">baomidou.com</a>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.objectstorage.infra.persistence.mapper;
