/// XML 解析适配器包。
///
/// 实现 Domain 层 {@link com.patra.catalog.domain.port.XmlParserPort} 端口，提供 MeSH XML 文件的流式解析能力。
///
/// ## 职责
///
/// - **流式解析**：使用 StAX（Streaming API for XML）逐元素解析 XML，支持大文件
///   - **内存可控**：不一次性加载整个 XML 到内存（299MB 文件仅占用 <2GB 内存）
///   - **惰性求值**：返回 {@link java.util.stream.Stream}，由调用方控制处理速度
///   - **错误容错**：格式错误时跳过记录并记录日志，不中断整个解析流程
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.infra.parser.StaxXmlParserImpl} - StAX XML 解析器实现
///
/// - 解析 MeSH Descriptor XML（主题词 + 树形编号 + 入口术语 + 概念）
///       - 解析 MeSH Qualifier XML（限定词）
///       - 使用自定义 Spliterator 封装流式解析逻辑
///       - 返回 Stream<MeshDescriptorAggregate>、Stream<MeshQualifierAggregate> 等
///
/// ## 设计原则
///
/// - **流式处理**：使用 XMLStreamReader 逐元素读取，避免 DOM/SAX 的高内存占用
///   - **Spliterator 封装**：将 XMLStreamReader 封装为 Spliterator，转换为 Stream API
///   - **资源管理**：Stream.onClose() 确保 XMLStreamReader 关闭
///   - **错误容错**：解析异常时记录日志并跳过当前记录，继续处理后续记录
///
/// ## 性能特征
///
/// | 指标 | 值 | 说明 |
/// |------|---|------|
/// | 文件大小 | 299 MB | MeSH Descriptor XML |
/// | 内存占用 | <2 GB | 流式处理，不全部加载到内存 |
/// | 处理速度 | ~1000 条/秒 | 取决于硬件和批次大小 |
/// | 记录总数 | ~550,000 条 | 5 张表合计 |
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：解析 Descriptor XML（流式处理）
/// @Service
/// @RequiredArgsConstructor
/// public class MeshImportOrchestrator {
///
///     private final XmlParserPort xmlParser;
///
///     public void parseDescriptors(File xmlFile) {
///         try (InputStream is = new FileInputStream(xmlFile);
///              Stream<MeshDescriptorAggregate> stream = xmlParser.parseDescriptors(is)) {
///
///             // 流式处理：分批保存到数据库
///             AtomicInteger batchNum = new AtomicInteger(0);
///             stream
///                 .filter(descriptor -> descriptor.getDescriptorUi() != null) // 过滤无效数据
///                 .collect(Collectors.groupingBy(d -> batchNum.getAndIncrement() / 1000)) // 分批
///                 .values()
///                 .forEach(batch -> {
///                     meshDescriptorRepository.batchSave(batch);
///                     log.info("保存批次 {}，记录数：{}", batchNum.get() / 1000, batch.size());
///                 });
///         }
///     }
/// }
///
/// // 示例 2：解析 Qualifier XML
/// try (InputStream is = new FileInputStream(xmlFile);
///      Stream<MeshQualifierAggregate> stream = xmlParser.parseQualifiers(is)) {
///
///     // Qualifier 数量少（约 80 条），一次性导入
///     List<MeshQualifierAggregate> qualifiers = stream.toList();
///     meshQualifierRepository.batchSave(qualifiers);
///     log.info("保存 Qualifier，记录数：{}", qualifiers.size());
/// }
///
/// // 示例 3：错误容错
/// // 如果 XML 中某条记录格式错误：
/// // - 记录警告日志："解析 Descriptor 失败，跳过记录：<DescriptorRecord>...</DescriptorRecord>"
/// // - 继续解析后续记录，不中断整个流程
/// ```
///
/// ## Spliterator 实现
///
/// 每种数据类型有独立的 Spliterator 实现：
///
/// - `DescriptorSpliterator` - 解析 &lt;DescriptorRecord&gt;
/// - `QualifierSpliterator` - 解析 &lt;QualifierRecord&gt;
/// - `TreeNumberSpliterator` - 从 Descriptor 中提取 &lt;TreeNumber&gt;
/// - `EntryTermSpliterator` - 从 Descriptor 中提取 &lt;Term&gt;
/// - `ConceptSpliterator` - 从 Descriptor 中提取 &lt;Concept&gt;
///
/// ```java
/// // Spliterator 模式
/// public class DescriptorSpliterator implements Spliterator<MeshDescriptorAggregate> {
///     private final XMLStreamReader reader;
///
///     @Override
///     public boolean tryAdvance(Consumer<? super MeshDescriptorAggregate> action) {
///         // 读取下一个 <DescriptorRecord> 元素
///         // 解析为 MeshDescriptorAggregate
///         // 调用 action.accept(descriptor)
///         // 返回 true 如果还有更多记录
///     }
/// }
/// ```
///
/// ## 错误处理
///
/// | 异常场景 | 处理策略 | 日志级别 |
/// |---------|---------|---------|
/// | XML 格式错误（整体） | 抛出 RuntimeException，中止解析 | ERROR |
/// | 单条记录格式错误 | 记录日志，跳过该记录，继续处理 | WARN |
/// | 必填字段缺失 | 跳过该记录 | WARN |
/// | 文件读取失败 | 抛出 RuntimeException | ERROR |
///
/// ## 架构位置
///
/// **Infrastructure 层 - 出站适配器**：
///
/// - 六边形架构的出站适配器（Outbound Adapter）
/// - 实现 Domain 层定义的 Port 接口
/// - 封装 XML 解析技术细节（StAX、Spliterator）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.infra.parser;
