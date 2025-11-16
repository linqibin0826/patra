/**
 * Storage 基础设施层仓储实现包。
 *
 * <p>本包包含 patra-object-storage 服务的仓储实现,作为六边形架构基础设施层的一部分。 仓储实现类是被驱动适配器,负责实现领域层定义的仓储端口,提供聚合根的持久化功能。
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.objectstorage.infra.persistence.repository.FileMetadataRepositoryImpl} -
 *       文件元数据仓储实现,使用 MyBatis-Plus 作为 ORM 框架
 * </ul>
 *
 * <h2>仓储职责</h2>
 *
 * <ul>
 *   <li><strong>实现端口契约</strong>: 实现领域层定义的 {@link
 *       com.patra.objectstorage.domain.port.FileMetadataRepository} 接口
 *   <li><strong>数据对象转换</strong>: 在领域模型(聚合根)和数据模型(DO)之间进行双向转换
 *   <li><strong>持久化操作</strong>: 执行数据库 CRUD 操作(通过 MyBatis Mapper)
 *   <li><strong>查询封装</strong>: 将数据库查询结果转换为领域对象
 *   <li><strong>事务支持</strong>: 参与应用层定义的事务,确保数据一致性
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>端口实现</strong>: 实现领域层端口接口,遵循依赖倒置原则
 *   <li><strong>技术隔离</strong>: 仓储实现依赖 MyBatis/Spring,但领域层不感知
 *   <li><strong>聚合根操作</strong>: 仓储以聚合根为单位进行操作,而非表或实体片段
 *   <li><strong>防御性编程</strong>: 对数据库返回的数据进行空值检查和异常处理
 *   <li><strong>查询优化</strong>: 使用索引、批量操作等优化查询性能
 * </ul>
 *
 * <h2>FileMetadataRepositoryImpl 实现</h2>
 *
 * 文件元数据仓储实现,负责聚合根的持久化和查询:
 *
 * <ul>
 *   <li>{@code save(FileMetadata)} - 保存或更新聚合根
 *       <ul>
 *         <li>根据聚合根是否有 ID 判断执行 insert 还是 update
 *         <li>保存后重新查询获取数据库生成的字段(如 ID、version、时间戳)
 *         <li>将数据对象转换回领域对象并返回
 *       </ul>
 *   <li>{@code findByStorageKey(StorageKey)} - 通过存储键查询聚合根
 *       <ul>
 *         <li>使用唯一索引 uk_storage_key 查询
 *         <li>返回 Optional,避免返回 null
 *         <li>将数据对象转换为领域对象
 *       </ul>
 * </ul>
 *
 * <h2>实现示例</h2>
 *
 * <pre>{@code
 * @Repository
 * @RequiredArgsConstructor
 * public class FileMetadataRepositoryImpl implements FileMetadataRepository {
 *
 *     private final FileMetadataMapper mapper;        // MyBatis Mapper
 *     private final FileMetadataConverter converter;  // 领域对象 <-> 数据对象转换器
 *
 *     @Override
 *     public FileMetadata save(FileMetadata metadata) {
 *         // 1. 将聚合根转换为数据对象
 *         FileMetadataDO dataObject = converter.toDO(metadata);
 *
 *         // 2. 根据是否有 ID 判断执行 insert 还是 update
 *         if (metadata.getId() == null) {
 *             mapper.insert(dataObject);  // 新增,数据库生成 ID
 *         } else {
 *             mapper.updateById(dataObject);  // 更新,使用现有 ID
 *         }
 *
 *         // 3. 重新查询获取数据库生成的字段(如 ID、version、审计时间戳)
 *         FileMetadataDO persisted = mapper.selectById(dataObject.getId());
 *
 *         // 4. 将数据对象转换回聚合根
 *         return converter.toAggregate(persisted);
 *     }
 *
 *     @Override
 *     public Optional<FileMetadata> findByStorageKey(StorageKey storageKey) {
 *         // 1. 通过唯一索引查询数据对象
 *         FileMetadataDO dataObject = mapper.findByStorageKey(storageKey.fullKey());
 *
 *         // 2. 转换为聚合根并包装为 Optional
 *         return Optional.ofNullable(converter.toAggregate(dataObject));
 *     }
 * }
 * }</pre>
 *
 * <h2>数据对象转换</h2>
 *
 * 仓储实现依赖转换器在领域模型和数据模型之间进行转换:
 *
 * <ul>
 *   <li><strong>toAggregate(DO → 聚合根)</strong>: 使用 {@code FileMetadata.restore()} 工厂方法重建聚合根
 *   <li><strong>toDO(聚合根 → DO)</strong>: 提取聚合根字段填充数据对象
 *   <li><strong>值对象转换</strong>: 转换 StorageKey、FileSize、FileChecksum、BusinessContext 等值对象
 *   <li><strong>枚举转换</strong>: 转换 FileStatus、StorageProvider 枚举类型
 * </ul>
 *
 * 详见 {@link com.patra.objectstorage.infra.persistence.converter.FileMetadataConverter}。
 *
 * <h2>MyBatis Mapper</h2>
 *
 * 仓储实现通过 MyBatis Mapper 执行数据库操作:
 *
 * <ul>
 *   <li>{@code insert(FileMetadataDO)} - 插入新记录,数据库生成主键 ID
 *   <li>{@code updateById(FileMetadataDO)} - 根据 ID 更新记录
 *   <li>{@code selectById(Long)} - 根据 ID 查询记录
 *   <li>{@code findByStorageKey(String)} - 根据 storage_key 唯一索引查询记录
 * </ul>
 *
 * 详见 {@link com.patra.objectstorage.infra.persistence.mapper.FileMetadataMapper}。
 *
 * <h2>唯一约束处理</h2>
 *
 * 数据库表 storage_file_metadata 的 storage_key 字段有唯一索引,确保幂等性:
 *
 * <pre>{@code
 * -- 数据库表定义
 * CREATE TABLE storage_file_metadata (
 *     id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 *     storage_key VARCHAR(768) NOT NULL,
 *     -- 其他字段...
 *     UNIQUE KEY uk_storage_key (storage_key)  -- 唯一索引
 * );
 * }</pre>
 *
 * 如果尝试插入重复的 storage_key,数据库会抛出 DuplicateKeyException:
 *
 * <pre>{@code
 * try {
 *     repository.save(metadata);
 * } catch (DuplicateKeyException e) {
 *     log.error("文件已存在: storageKey={}", metadata.getStorageKey().fullKey());
 *     throw new BusinessException("文件已记录,不允许重复上传");
 * }
 * }</pre>
 *
 * <h2>乐观锁处理</h2>
 *
 * 聚合根使用 version 字段实现乐观锁,防止并发更新冲突:
 *
 * <pre>{@code
 * -- MyBatis-Plus 自动处理乐观锁
 * UPDATE storage_file_metadata
 * SET version = version + 1,
 *     updated_at = NOW(),
 *     ...
 * WHERE id = ? AND version = ?;  -- version 作为更新条件
 * }</pre>
 *
 * 如果并发更新导致 version 不匹配,MyBatis-Plus 会抛出 OptimisticLockerInnerInterceptor 异常。
 *
 * <h2>测试示例</h2>
 *
 * <pre>{@code
 * @SpringBootTest
 * @Transactional
 * class FileMetadataRepositoryImplTest {
 *
 *     @Autowired
 *     private FileMetadataRepository repository;
 *
 *     @Test
 *     void testSaveAndFind() {
 *         // 1. 创建聚合根
 *         FileMetadata metadata = FileMetadata.create(
 *             new StorageKey("test-bucket", "test-key"),
 *             new FileSize(1024L),
 *             new FileChecksum("md5hash", null),
 *             new BusinessContext("test-service", "test-type", "test-id", Map.of()),
 *             StorageProvider.MINIO
 *         );
 *
 *         // 2. 保存聚合根
 *         FileMetadata saved = repository.save(metadata);
 *         assertNotNull(saved.getId());  // 验证 ID 已生成
 *
 *         // 3. 查询聚合根
 *         Optional<FileMetadata> found = repository.findByStorageKey(
 *             new StorageKey("test-bucket", "test-key")
 *         );
 *         assertTrue(found.isPresent());
 *         assertEquals(saved.getId(), found.get().getId());
 *     }
 * }
 * }</pre>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>仓储端口: {@link com.patra.objectstorage.domain.port.FileMetadataRepository}
 *   <li>转换器: {@link com.patra.objectstorage.infra.persistence.converter.FileMetadataConverter}
 *   <li>Mapper: {@link com.patra.objectstorage.infra.persistence.mapper.FileMetadataMapper}
 *   <li>数据对象: {@link com.patra.objectstorage.infra.persistence.entity.FileMetadataDO}
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.objectstorage.infra.persistence.repository;
