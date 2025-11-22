/// Storage 基础设施层数据对象转换器包。
/// 
/// 本包包含领域模型与数据模型之间的双向转换器,作为六边形架构基础设施层的一部分。 转换器负责在聚合根和数据对象之间进行映射,隔离领域层和持久化层的实现细节。
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.objectstorage.infra.persistence.converter.FileMetadataConverter} - 文件元数据转换器,使用
///       MapStruct 实现领域模型和数据模型的双向转换
/// 
/// ## 转换器职责
/// 
/// - **领域 → 数据**: 将聚合根转换为数据对象(DO),用于数据库持久化
///   - **数据 → 领域**: 将数据对象转换为聚合根,用于业务逻辑处理
///   - **值对象转换**: 转换 StorageKey、FileSize、FileChecksum、BusinessContext 等值对象
///   - **枚举转换**: 转换 FileStatus、StorageProvider 枚举类型
///   - **复杂类型映射**: 处理 JSON 字段、二进制字段、时间戳等复杂类型
/// 
/// ## 设计原则
/// 
/// - **使用 MapStruct**: 通过注解处理器在编译期生成转换代码,性能高且类型安全
///   - **显式映射**: 避免隐式类型转换,所有映射规则明确定义
///   - **空值处理**: 对可选字段执行空值检查,防止 NullPointerException
///   - **防御性拷贝**: 对可变字段(如 Map、数组)执行防御性拷贝
///   - **单向依赖**: 转换器依赖领域层和数据层,但两者不依赖转换器
/// 
/// ## FileMetadataConverter 转换器
/// 
/// 文件元数据转换器定义了以下映射方法:
/// 
/// - `toAggregate(FileMetadataDO)` - 将数据对象转换为聚合根
///       
/// - 使用 `FileMetadata.restore()` 工厂方法重建聚合根
///         - 转换值对象(StorageKey、FileSize、FileChecksum、BusinessContext)
///         - 转换枚举类型(FileStatus、StorageProvider)
///         - 处理 JSON 字段(correlationData、recordRemarks)
/// 
///   - `toDO(FileMetadata)` - 将聚合根转换为数据对象
///       
/// - 提取聚合根字段填充数据对象
///         - 分解值对象为基本类型字段
///         - 序列化 Map 为 JSON 字符串
/// 
/// ## MapStruct 注解示例
/// 
/// ```java
/// @Mapper(componentModel = "spring")
/// public interface FileMetadataConverter {
/// 
///     *      * 将数据对象转换为聚合根。
///      *
///      * @param dataObject 数据对象(可能为 null)
///      * @return 聚合根,如果输入为 null 则返回 null
///      *\/
///     @Mapping(target = "storageKey", expression = "java(toStorageKey(dataObject))")
///     @Mapping(target = "fileSize", expression = "java(new FileSize(dataObject.getFileSize()))")
///     @Mapping(target = "checksum", expression = "java(toChecksum(dataObject))")
///     @Mapping(target = "context", expression = "java(toContext(dataObject))")
///     FileMetadata toAggregate(FileMetadataDO dataObject);
/// 
///     *      * 将聚合根转换为数据对象。
///      *
///      * @param aggregate 聚合根(可能为 null)
///      * @return 数据对象,如果输入为 null 则返回 null
///      *\/
///     @Mapping(target = "storageKey", expression = "java(aggregate.getStorageKey().fullKey())")
///     @Mapping(target = "bucketName", expression = "java(aggregate.getStorageKey().bucket())")
///     @Mapping(target = "objectKey", expression = "java(aggregate.getStorageKey().objectKey())")
///     @Mapping(target = "fileSize", expression = "java(aggregate.getFileSize().bytes())")
///     @Mapping(target = "md5Hash", expression = "java(aggregate.getChecksum().md5Hash())")
///     @Mapping(target = "sha256Hash", expression = "java(aggregate.getChecksum().sha256Hash())")
///     FileMetadataDO toDO(FileMetadata aggregate);
/// 
///     // 辅助方法:转换值对象
///     default StorageKey toStorageKey(FileMetadataDO dataObject) {
///         if (dataObject == null) return null;
///         return new StorageKey(dataObject.getBucketName(), dataObject.getObjectKey());
/// 
///     default FileChecksum toChecksum(FileMetadataDO dataObject) {
///         if (dataObject == null) return null;
///         return new FileChecksum(dataObject.getMd5Hash(), dataObject.getSha256Hash());
/// 
///     default BusinessContext toContext(FileMetadataDO dataObject) {
///         if (dataObject == null) return null;
///         return new BusinessContext(
///             dataObject.getServiceName(),
///             dataObject.getBusinessType(),
///             dataObject.getBusinessId(),
///             parseCorrelationData(dataObject.getCorrelationData())
///         );
/// ```
/// 
/// ## 复杂类型转换
/// 
/// **JSON 字段转换**:
/// 
/// ```java
/// // 领域层:Map<String, Object>
/// BusinessContext context = new BusinessContext(
///     "patra-ingest",
///     "publication_batch",
///     "batch-001",
///     Map.of("pmcId", "PMC12345678", "sourceId", "pubmed")
/// );
/// 
/// // 数据层:String(JSON 格式)
/// FileMetadataDO dataObject = converter.toDO(metadata);
/// String json = dataObject.getCorrelationData();
/// // {"pmcId":"PMC12345678","sourceId":"pubmed"
/// ```
/// 
/// **二进制字段转换**:
/// 
/// ```java
/// // 领域层:byte[] ipAddress
/// FileMetadata metadata = ...;
/// byte[] ip = metadata.getIpAddress();  // 防御性拷贝
/// 
/// // 数据层:byte[] ipAddress
/// FileMetadataDO dataObject = converter.toDO(metadata);
/// byte[] ipCopy = dataObject.getIpAddress();  // 再次拷贝
/// ```
/// 
/// **枚举类型转换**:
/// 
/// ```java
/// // 领域层:FileStatus 枚举
/// FileStatus status = FileStatus.ACTIVE;
/// 
/// // 数据层:FileStatus 枚举(直接映射,MyBatis-Plus 自动转换为字符串)
/// FileMetadataDO dataObject = new FileMetadataDO();
/// dataObject.setStatus(FileStatus.ACTIVE);
/// // 数据库存储为 "ACTIVE"
/// ```
/// 
/// ## 转换器使用示例
/// 
/// **在仓储实现中使用**:
/// 
/// ```java
/// @Repository
/// @RequiredArgsConstructor
/// public class FileMetadataRepositoryImpl implements FileMetadataRepository {
/// 
///     private final FileMetadataMapper mapper;
///     private final FileMetadataConverter converter;  // 注入转换器
/// 
///     @Override
///     public FileMetadata save(FileMetadata metadata) {
///         // 1. 聚合根 → 数据对象
///         FileMetadataDO dataObject = converter.toDO(metadata);
/// 
///         // 2. 持久化数据对象
///         if (metadata.getId() == null) {
///             mapper.insert(dataObject); else {
///             mapper.updateById(dataObject);
/// 
///         // 3. 重新查询
///         FileMetadataDO persisted = mapper.selectById(dataObject.getId());
/// 
///         // 4. 数据对象 → 聚合根
///         return converter.toAggregate(persisted);
/// 
///     @Override
///     public Optional<FileMetadata> findByStorageKey(StorageKey storageKey) {
///         FileMetadataDO dataObject = mapper.findByStorageKey(storageKey.fullKey());
///         // 数据对象 → 聚合根
///         return Optional.ofNullable(converter.toAggregate(dataObject));
/// ```
/// 
/// ## MapStruct 编译期生成代码
/// 
/// MapStruct 在编译期生成实现类,避免运行时反射开销:
/// 
/// ```java
/// // 生成的实现类(target/generated-sources/annotations/)
/// @Component
/// public class FileMetadataConverterImpl implements FileMetadataConverter {
/// 
///     @Override
///     public FileMetadata toAggregate(FileMetadataDO dataObject) {
///         if (dataObject == null) {
///             return null;
/// 
///         return FileMetadata.restore(
///             dataObject.getId(),
///             toStorageKey(dataObject),
///             new FileSize(dataObject.getFileSize()),
///             dataObject.getContentType(),
///             toChecksum(dataObject),
///             toContext(dataObject),
///             dataObject.getProvider(),
///             dataObject.getStatus(),
///             dataObject.getUploadedAt(),
///             dataObject.getExpiresAt(),
///             dataObject.getDeletedAt(),
///             dataObject.getRecordRemarks(),
///             dataObject.getVersion(),
///             dataObject.getIpAddress(),
///             dataObject.getCreatedAt(),
///             dataObject.getCreatedBy(),
///             dataObject.getCreatedByName(),
///             dataObject.getUpdatedAt(),
///             dataObject.getUpdatedBy(),
///             dataObject.getUpdatedByName(),
///             dataObject.getDeleted()
///         );
/// ```
/// 
/// ## 性能优势
/// 
/// - **编译期生成**: 避免运行时反射,性能接近手写代码
///   - **类型安全**: 编译期检查映射规则,避免运行时类型错误
///   - **代码可读**: 生成的代码可调试,便于问题排查
///   - **零配置**: 无需 XML 配置,注解驱动即可
/// 
/// ## 相关文档
/// 
/// - 聚合根: {@link com.patra.objectstorage.domain.model.aggregate.FileMetadata}
///   - 数据对象: {@link com.patra.objectstorage.infra.persistence.entity.FileMetadataDO}
///   - 仓储实现: {@link com.patra.objectstorage.infra.persistence.repository.FileMetadataRepositoryImpl}
///   - MapStruct 官方文档: <a href="https://mapstruct.org/">mapstruct.org</a>
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.objectstorage.infra.persistence.converter;
