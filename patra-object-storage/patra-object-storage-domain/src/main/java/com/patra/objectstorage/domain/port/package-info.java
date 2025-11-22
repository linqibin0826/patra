/// Storage 领域层端口包。
///
/// 本包定义了 patra-object-storage 服务的领域层端口(Port),作为六边形架构的核心抽象。
/// 端口接口定义了领域层与外部基础设施层的交互契约,实现了依赖倒置原则,确保领域层独立于技术实现细节。
///
/// ## 核心端口
///
/// - {@link com.patra.objectstorage.domain.port.FileMetadataRepository} - 文件元数据仓储端口,定义聚合根的持久化操作
///
/// ## 端口类型
///
/// 在六边形架构中,端口分为两类:
///
/// - **驱动端口(Primary Port)**: 应用层暴露给外部的接口,如 REST API、消息监听器(本包不包含)
///   - **被驱动端口(Secondary Port)**: 领域层依赖的外部服务接口,如仓储、外部 API(本包定义)
///
/// {@link com.patra.objectstorage.domain.port.FileMetadataRepository} 是被驱动端口,由基础设施层的适配器实现。
///
/// ## 设计原则
///
/// - **依赖倒置**: 领域层定义接口,基础设施层提供实现,依赖方向从外向内
///   - **纯 Java 实现**: 禁止依赖 Spring、MyBatis 等框架,保持领域层纯净
///   - **领域语言**: 接口方法使用领域术语,而非技术术语(如 save 而非 insert/update)
///   - **聚合根操作**: 仓储接口以聚合根为单位进行操作,不操作值对象或实体片段
///   - **返回领域对象**: 仓储方法返回领域对象,而非数据对象或 DTO
///
/// ## FileMetadataRepository 端口
///
/// 文件元数据仓储端口定义了聚合根的持久化操作:
///
/// - `save(FileMetadata)` - 保存或更新聚合根
///   - `findByStorageKey(StorageKey)` - 通过存储键查询聚合根
///
/// ## 端口使用示例
///
/// **在应用层使用端口**:
///
/// ```java
/// @Service
/// @RequiredArgsConstructor
/// public class RecordUploadOrchestrator {
///
///     // 依赖端口抽象,而非具体实现
///     private final FileMetadataRepository repository;
///
///     @Transactional
///     public RecordUploadResult execute(RecordUploadCommand command) {
///         // 创建聚合根
///         FileMetadata metadata = FileMetadata.create(...);
///
///         // 通过端口保存聚合根
///         FileMetadata saved = repository.save(metadata);
///
///         return new RecordUploadResult(saved.getId(), saved.getUploadedAt());
/// ```
///
/// **在基础设施层实现端口**:
///
/// ```java
/// @Repository
/// @RequiredArgsConstructor
/// public class FileMetadataRepositoryImpl implements FileMetadataRepository {
///
///     private final FileMetadataMapper mapper;        // MyBatis Mapper
///     private final FileMetadataConverter converter;  // 领域对象 <-> 数据对象转换器
///
///     @Override
///     public FileMetadata save(FileMetadata metadata) {
///         // 转换为数据对象
///         FileMetadataDO dataObject = converter.toDO(metadata);
///
///         // 根据是否有 ID 判断执行 insert 还是 update
///         if (metadata.getId() == null) {
///             mapper.insert(dataObject); else {
///             mapper.updateById(dataObject);
///
///         // 重新查询获取数据库生成的字段(如 ID、version、时间戳)
///         FileMetadataDO persisted = mapper.selectById(dataObject.getId());
///
///         // 转换回领域对象
///         return converter.toAggregate(persisted);
///
///     @Override
///     public Optional<FileMetadata> findByStorageKey(StorageKey storageKey) {
///         FileMetadataDO dataObject = mapper.findByStorageKey(storageKey.fullKey());
///         return Optional.ofNullable(converter.toAggregate(dataObject));
/// ```
///
/// ## 端口与适配器的关系
///
/// ```
///
/// ┌────────────────────────────────────────────────────────────┐
/// │                       应用层(app)                            │
/// │              RecordUploadOrchestrator                      │
/// │                        ↓                                   │
/// │              依赖 FileMetadataRepository 端口                │
/// └────────────────────────────┬───────────────────────────────┘
///                              │
///                              │ 依赖倒置
///                              │
/// ┌────────────────────────────▼───────────────────────────────┐
/// │                       领域层(domain)                         │
/// │          FileMetadataRepository 端口接口(抽象)                │
/// └────────────────────────────▲───────────────────────────────┘
///                              │
///                              │ 实现
///                              │
/// ┌────────────────────────────┴───────────────────────────────┐
/// │                   基础设施层(infra)                          │
/// │        FileMetadataRepositoryImpl 适配器(实现)                │
/// │              ↓                                             │
/// │    MyBatis Mapper + 数据对象转换                             │
/// └────────────────────────────────────────────────────────────┘
///
/// ```
///
/// ## 端口方法设计规范
///
/// - **命名**: 使用领域语言,如 save/find/delete,避免 insert/select/remove
///   - **参数**: 接受领域对象(聚合根、值对象),而非原始类型或 DTO
///   - **返回值**: 返回领域对象或 Optional,避免返回 null
///   - **异常**: 抛出领域异常,而非技术异常(如 DataAccessException)
///   - **事务**: 端口接口不定义事务边界,由应用层编排器管理
///
/// ## 为什么需要端口
///
/// - **依赖倒置**: 领域层不依赖基础设施层,易于测试和替换实现
///   - **可测试性**: 可使用 Mock 对象替代真实实现,进行单元测试
///   - **技术独立**: 可轻松切换持久化技术(MyBatis → JPA → MongoDB)
///   - **清晰边界**: 明确领域层和基础设施层的职责边界
///
/// ## 测试示例
///
/// ```java
/// @Test
/// void testRecordUpload() {
///     // 创建 Mock 端口
///     FileMetadataRepository mockRepository = mock(FileMetadataRepository.class);
///
///     // 定义 Mock 行为
///     when(mockRepository.save(any(FileMetadata.class)))
///         .thenAnswer(invocation -> {
///             FileMetadata metadata = invocation.getArgument(0);
///             metadata.assignId(123456L);
///             return metadata;);
///
///     // 注入 Mock 端口
///     RecordUploadOrchestrator orchestrator = new RecordUploadOrchestrator(mockRepository);
///
///     // 执行测试
///     RecordUploadResult result = orchestrator.execute(command);
///
///     // 验证结果
///     assertEquals(123456L, result.metadataId());
///     verify(mockRepository).save(any(FileMetadata.class));
/// ```
///
/// ## 相关文档
///
/// - 聚合根: {@link com.patra.objectstorage.domain.model.aggregate.FileMetadata}
///   - 仓储实现: `patra-object-storage-infra/persistence/repository/FileMetadataRepositoryImpl`
///   - 六边形架构: <a
///
// href="https://alistair.cockburn.us/hexagonal-architecture/">alistair.cockburn.us/hexagonal-architecture/</a>
///
/// @author linqibin
/// @since 0.1.0
package com.patra.objectstorage.domain.port;
