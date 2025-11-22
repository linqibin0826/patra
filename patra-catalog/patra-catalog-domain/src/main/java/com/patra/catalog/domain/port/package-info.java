/// Catalog Port 接口包（依赖倒置）。
/// 
/// 定义 Domain 层需要的外部能力接口（Port），由 Infrastructure 层实现，实现依赖倒置原则。
/// 
/// ## 职责
/// 
/// - 定义仓储接口（Repository Port）：用于聚合根的持久化和查询
///   - 定义外部服务接口：用于调用外部系统能力（如文件下载、XML 解析）
///   - 抽象技术细节：Domain 层只依赖接口，不依赖具体技术实现（MyBatis、Spring、StAX 等）
///   - 支持可测试性：Application 层可以 Mock Port 接口进行单元测试
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.catalog.domain.port.MeshImportPort} - MeSH 导入任务仓储接口
///     
/// - save(MeshImportAggregate)：保存或更新导入任务聚合根
///       - findById(MeshImportId)：根据任务 ID 查询聚合根
///       - findRunningTask()：查询正在运行的任务
///       - existsRunningTask()：检查是否有正在运行的任务
///   - {@link com.patra.catalog.domain.port.MeshBatchDetailPort} - MeSH 批次详情仓储接口
///     
/// - findFailedBatches(MeshImportId)：查询失败批次列表
///       - countByStatus(MeshImportId, MeshBatchStatus)：统计某状态批次数量
///   - {@link com.patra.catalog.domain.port.XmlParserPort} - XML 解析器接口
///     
/// - parseDescriptors(InputStream)：流式解析主题词
///       - parseQualifiers(InputStream)：流式解析限定词
///       - parseTreeNumbers(InputStream)：流式解析树形编号
///       - parseEntryTerms(InputStream)：流式解析入口术语
///       - parseConcepts(InputStream)：流式解析概念
///   - {@link com.patra.catalog.domain.port.MeshFileDownloadPort} - MeSH 文件下载接口
///     
/// - download(String sourceUrl)：下载 XML 文件
///       - validateChecksum(File xmlFile, String expectedHash)：验证文件校验和
///   - {@link com.patra.catalog.domain.port.MeshDescriptorPort} - MeSH 主题词仓储接口
///     
/// - save(MeshDescriptorAggregate)：保存主题词聚合根
///       - findById(DescriptorId)：根据主题词 ID 查询
///       - saveBatch(List<MeshDescriptorAggregate>)：批量保存主题词
/// 
/// ## 设计原则
/// 
/// - **依赖倒置**：Domain 层定义接口，Infrastructure 层实现接口，依赖方向从外向内
///   - **接口隔离**：每个 Port 接口只定义必要的方法，不混合多种职责
///   - **面向领域**：Port 接口方法参数和返回值使用领域对象（聚合根、实体、值对象），不使用 DO 对象
///   - **技术无关**：Port 接口不暴露技术细节（如 MyBatis、JPA），保持 Domain 层纯净
///   - **流式处理**：解析接口返回 Stream，支持大数据量流式处理，避免内存溢出
/// 
/// ## 使用示例
/// ```java
/// // 示例 1：Application 层使用 Port 接口
/// @Service
/// @RequiredArgsConstructor
/// public class MeshImportOrchestrator {
/// 
///     private final MeshImportPort meshImportPort;
///     private final XmlParserPort xmlParserPort;
///     private final MeshFileDownloadPort meshFileDownloadPort;
///     private final MeshDescriptorPort meshDescriptorPort;
/// 
///     @Transactional
///     public MeshImportResultDTO startImport(StartImportCommand command) {
///         // 1. 检查是否有正在运行的任务
///         if (meshImportPort.existsRunningTask()) {
///             throw new IllegalStateException("已有正在运行的任务");
/// 
///         // 2. 下载 XML 文件
///         File xmlFile = meshFileDownloadPort.download(command.sourceUrl());
/// 
///         // 3. 流式解析并保存数据
///         try (FileInputStream fis = new FileInputStream(xmlFile);
///              Stream<MeshDescriptorAggregate> stream = xmlParserPort.parseDescriptors(fis)) {
/// 
///             List<MeshDescriptorAggregate> batch = new ArrayList<>();
///             stream.forEach(descriptor -> {
///                 batch.add(descriptor);
///                 if (batch.size() >= 1000) {
///                     meshDescriptorPort.saveBatch(batch);
///                     batch.clear(););
/// 
///             // 保存最后一批
///             if (!batch.isEmpty()) {
///                 meshDescriptorPort.saveBatch(batch);
/// 
///         // 4. 保存任务聚合根
///         MeshImportAggregate aggregate = createPendingTask();
///         aggregate.startImport();
///         aggregate = meshImportPort.save(aggregate);
/// 
///         return buildResult(aggregate);
/// 
/// // 示例 2：Infrastructure 层实现 Port 接口
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
///         // 转换为 DO 对象
///         MeshImportTaskDO taskDO = meshImportConverter.toTaskDO(aggregate);
///         List<MeshTableProgressDO> progressDOList = meshImportConverter.toProgressDOList(aggregate);
/// 
///         // 保存到数据库
///         if (taskDO.getId() == null) {
///             meshImportTaskMapper.insert(taskDO); else {
///             meshImportTaskMapper.updateById(taskDO);
/// 
///         // 保存表进度
///         progressDOList.forEach(meshTableProgressMapper::insertOrUpdate);
/// 
///         // 转换回领域对象
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
/// // 示例 3：单元测试中 Mock Port 接口
/// @ExtendWith(MockitoExtension.class)
/// class MeshImportOrchestratorTest {
/// 
///     @Mock
///     private MeshImportPort meshImportPort;
/// 
///     @Mock
///     private XmlParserPort xmlParserPort;
/// 
///     @InjectMocks
///     private MeshImportOrchestrator orchestrator;
/// 
///     @Test
///     void shouldStartImportSuccessfully() {
///         // Given
///         when(meshImportPort.existsRunningTask()).thenReturn(false);
///         when(meshImportPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
/// 
///         StartImportCommand command = new StartImportCommand("https://...", "测试任务");
/// 
///         // When
///         MeshImportResultDTO result = orchestrator.startImport(command);
/// 
///         // Then
///         assertThat(result.status()).isEqualTo("PROCESSING");
///         verify(meshImportPort, times(1)).save(any());
/// ```
/// 
/// @since 0.2.0
/// @author Patra Team
package com.patra.catalog.domain.port;
