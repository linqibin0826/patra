/// Catalog 基础设施层技术实现包。
/// 
/// Infrastructure 层负责实现 Domain 层定义的 Port 接口，提供数据持久化、外部服务调用等技术能力。
/// 
/// ## 职责
/// 
/// - 数据持久化：实现 Repository Port 接口，使用 MyBatis-Plus 操作数据库
///   - 对象转换：使用 MapStruct 在领域对象和 DO 对象之间转换
///   - XML 解析：使用 StAX 流式解析 MeSH XML 文件
///   - HTTP 下载：使用 Spring RestClient 下载 MeSH 数据文件
///   - 技术实现：封装所有技术细节，对 Domain 层和 Application 层透明
/// 
/// ## 核心组件
/// 
/// - **persistence.repository 包** - Repository 实现
///     
/// - {@link com.patra.catalog.infra.persistence.repository.MeshImportRepositoryImpl} - MeSH 导入任务仓储实现
///         
/// - 实现 MeshImportPort 接口
///           - 使用 MeshImportTaskMapper 和 MeshTableProgressMapper 操作数据库
///           - 使用 MeshImportConverter 转换领域对象和 DO 对象
///       - {@link com.patra.catalog.infra.persistence.repository.MeshBatchDetailRepositoryImpl} - MeSH 批次详情仓储实现（规划中）
///       - {@link com.patra.catalog.infra.persistence.repository.MeshDescriptorRepositoryImpl} - MeSH 主题词仓储实现
///   - **persistence.mapper 包** - MyBatis Mapper 接口
///     
/// - {@link com.patra.catalog.infra.persistence.mapper.MeshImportTaskMapper} - 导入任务表 Mapper
///       - {@link com.patra.catalog.infra.persistence.mapper.MeshTableProgressMapper} - 表进度表 Mapper
///       - {@link com.patra.catalog.infra.persistence.mapper.MeshBatchDetailMapper} - 批次详情表 Mapper
///       - {@link com.patra.catalog.infra.persistence.mapper.MeshDescriptorMapper} - 主题词表 Mapper
///   - **persistence.entity 包** - 数据库实体（DO）
///     
/// - {@link com.patra.catalog.infra.persistence.entity.MeshImportTaskDO} - 导入任务表实体
///       - {@link com.patra.catalog.infra.persistence.entity.MeshTableProgressDO} - 表进度表实体
///       - {@link com.patra.catalog.infra.persistence.entity.MeshBatchDetailDO} - 批次详情表实体
///       - {@link com.patra.catalog.infra.persistence.entity.MeshDescriptorDO} - 主题词表实体
///   - **persistence.converter 包** - MapStruct 对象转换器
///     
/// - {@link com.patra.catalog.infra.persistence.converter.MeshImportConverter} - 导入任务对象转换器
///         
/// - toDomain(TaskDO, List<ProgressDO>)：DO → 聚合根
///           - toTaskDO(MeshImportAggregate)：聚合根 → TaskDO
///           - toProgressDOList(MeshImportAggregate)：聚合根 → List<ProgressDO>
///       - {@link com.patra.catalog.infra.persistence.converter.MeshDescriptorConverter} - 主题词对象转换器
///   - **parser 包** - XML 解析器实现
///     
/// - {@link com.patra.catalog.infra.parser.StaxXmlParserImpl} - StAX 流式 XML 解析器
///         
/// - 实现 XmlParserPort 接口
///           - 使用 JDK 内置 StAX（javax.xml.stream.XMLStreamReader）
///           - 返回 Stream，支持大数据量流式处理，避免内存溢出
///   - **download 包** - 文件下载实现
///     
/// - {@link com.patra.catalog.infra.download.RestClientMeshFileDownloadImpl} - 基于 RestClient 的文件下载器
///         
/// - 实现 MeshFileDownloadPort 接口
///           - 使用 Spring RestClient（底层 JDK 21 HttpClient）
/// 
/// ## 设计原则
/// 
/// - **依赖倒置**：Infrastructure 层实现 Domain 层定义的 Port 接口，依赖方向从外向内
///   - **技术隔离**：所有技术细节封装在 Infrastructure 层，Domain 层和 Application 层不感知
///   - **DO 不泄露**：DO 对象只在 Infrastructure 层内部使用，不暴露到外层（通过 Converter 转换为领域对象）
///   - **最小依赖**：仅引入必要的技术依赖（如 spring-web 而非 spring-boot-starter-web）
///   - **流式处理**：使用 Stream 处理大数据量，避免一次性加载到内存
/// 
/// ## 使用示例
/// ```java
/// // 示例 1：Repository 实现（实现 Port 接口）
/// @Repository
/// @RequiredArgsConstructor
/// public class MeshImportRepositoryImpl implements MeshImportPort {
/// 
///     private final MeshImportTaskMapper meshImportTaskMapper;
///     private final MeshTableProgressMapper meshTableProgressMapper;
///     private final MeshImportConverter meshImportConverter;
/// 
///     @Override
///     public MeshImportAggregate save(MeshImportAggregate aggregate) {
///         // 1. 转换为 DO 对象
///         MeshImportTaskDO taskDO = meshImportConverter.toTaskDO(aggregate);
///         List<MeshTableProgressDO> progressDOList = meshImportConverter.toProgressDOList(aggregate);
/// 
///         // 2. 保存到数据库
///         if (taskDO.getId() == null) {
///             meshImportTaskMapper.insert(taskDO); else {
///             meshImportTaskMapper.updateById(taskDO);
/// 
///         // 3. 保存表进度
///         progressDOList.forEach(progress -> {
///             progress.setImportId(taskDO.getId());
///             meshTableProgressMapper.insertOrUpdate(progress););
/// 
///         // 4. 转换回领域对象
///         return meshImportConverter.toDomain(taskDO, progressDOList);
/// 
///     @Override
///     public Optional<MeshImportAggregate> findById(MeshImportId id) {
///         MeshImportTaskDO taskDO = meshImportTaskMapper.selectById(id.value());
///         if (taskDO == null) {
///             return Optional.empty();
/// 
///         List<MeshTableProgressDO> progressDOList =
///             meshTableProgressMapper.findByImportId(taskDO.getId());
/// 
///         return Optional.of(meshImportConverter.toDomain(taskDO, progressDOList));
/// 
/// // 示例 2：MapStruct 对象转换器
/// @Mapper(componentModel = "spring")
/// public interface MeshImportConverter {
/// 
///     // DO → 领域对象
///     @Mapping(target = "id", source = "taskDO.id", qualifiedByName = "toMeshImportId")
///     @Mapping(target = "tableProgressList", source = "progressDOList")
///     MeshImportAggregate toDomain(MeshImportTaskDO taskDO, List<MeshTableProgressDO> progressDOList);
/// 
///     // 领域对象 → DO
///     @Mapping(target = "id", source = "id.value")
///     MeshImportTaskDO toTaskDO(MeshImportAggregate aggregate);
/// 
///     List<MeshTableProgressDO> toProgressDOList(MeshImportAggregate aggregate);
/// 
///     @Named("toMeshImportId")
///     default MeshImportId toMeshImportId(Long id) {
///         return MeshImportId.of(id);
/// 
/// // 示例 3：StAX 流式 XML 解析器
/// @Component
/// @RequiredArgsConstructor
/// public class StaxXmlParserImpl implements XmlParserPort {
/// 
///     private final MeshImportConfig meshImportConfig;
/// 
///     @Override
///     public Stream<MeshDescriptorAggregate> parseDescriptors(InputStream inputStream) {
///         XMLInputFactory factory = XMLInputFactory.newInstance();
///         XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
/// 
///         return StreamSupport.stream(
///             Spliterators.spliteratorUnknownSize(
///                 new DescriptorIterator(reader),
///                 Spliterator.ORDERED
///             ),
///             false
///         ).onClose(() -> {
///             try {
///                 reader.close(); catch (XMLStreamException e) {
///                 throw new RuntimeException(e););
/// 
///     // 内部迭代器，逐个解析 Descriptor
///     private static class DescriptorIterator implements Iterator<MeshDescriptorAggregate> {
///         // ... 实现细节
/// 
/// // 示例 4：RestClient 文件下载器
/// @Component
/// @RequiredArgsConstructor
/// public class RestClientMeshFileDownloadImpl implements MeshFileDownloadPort {
/// 
///     private final MeshImportConfig meshImportConfig;
///     private final RestClient restClient;
/// 
///     @Override
///     public File download(String sourceUrl) {
///         File tempFile = Files.createTempFile("mesh-", ".xml").toFile();
/// 
///         restClient.get()
///             .uri(sourceUrl)
///             .retrieve()
///             .body((inputStream, httpHeaders) -> {
///                 Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
///                 return tempFile;);
/// 
///         return tempFile;
/// 
///     @Override
///     public boolean validateChecksum(File xmlFile, String expectedHash) {
///         String actualHash = DigestUtils.md5Hex(new FileInputStream(xmlFile));
///         return actualHash.equalsIgnoreCase(expectedHash);
/// ```
/// 
/// @since 0.2.0
/// @author Patra Team
package com.patra.catalog.infra;
