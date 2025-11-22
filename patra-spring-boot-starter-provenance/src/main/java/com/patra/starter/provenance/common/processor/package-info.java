/// 数据处理器核心包。
/// 
/// 定义通用的数据处理器契约和上下文管理，为各数据源提供者（如 PubMed、EPMC）提供统一的数据处理基础设施。
/// 
/// ## 职责
/// 
/// - 定义通用数据处理器接口 {@link DataProcessor}
///   - 提供处理器上下文管理 {@link ProviderContext}
///   - 规范处理结果和验证结果模型
///   - 支持数据获取、转换和验证的完整生命周期
/// 
/// ## 核心组件
/// 
/// - {@link DataProcessor} - 数据处理器接口，定义数据获取和转换逻辑
///   - {@link ProviderContext} - 处理器上下文，封装配置、客户端和扩展属性
///   - {@link ProcessResult} - 处理结果，包含数据、游标和错误信息
///   - {@link ValidationResult} - 验证结果，包含验证状态和错误详情
/// 
/// ## 架构位置
/// 
/// ```
/// 
/// ProvenanceDataProvider (如 PubmedDataProvider)
///     ↓ 委托
/// DataProcessor (如 PubmedPublicationProcessor) ← [本包]
///     ↓ 调用
/// 外部 API 客户端 (如 PubMedClient)
/// 
/// ```
/// 
/// ## 设计模式
/// 
/// - **策略模式** - DataProcessor 作为可替换的数据处理策略
///   - **模板方法** - 定义数据处理的标准流程（process → validate → transform）
///   - **依赖注入** - 通过 ProviderContext 注入运行时依赖
/// 
/// ## 核心流程
/// 
/// ```java
/// // 1. 准备上下文
/// ProviderContext context = ProviderContext.builder()
///     .config(config)
///     .client(pubMedClient)
///     .build();
/// 
/// // 2. 处理数据
/// ProcessResult<CanonicalPublication> result = processor.process(request, context);
/// 
/// // 3. 验证数据
/// for (CanonicalPublication publication : result.data()) {
///     ValidationResult validation = processor.validate(publication);
///     if (!validation.isValid()) {
///         log.warn("验证失败: {", validation.errors());
/// 
/// // 4. 转换数据（如果需要）
/// CanonicalPublication transformed = processor.transform(rawData);
/// ```
/// 
/// ## 实现 DataProcessor 示例
/// 
/// ```java
/// @RequiredArgsConstructor
/// public class PubmedPublicationProcessor implements DataProcessor<CanonicalPublication> {
/// 
///     private final PubMedClient pubMedClient;
///     private final PubmedPublicationConverter converter;
/// 
///     @Override
///     public DataType getDataType() {
///         return DataType.PUBLICATION;
/// 
///     @Override
///     public ProcessResult<CanonicalPublication> process(
///             ProviderRequest request,
///             ProviderContext context) {
///         // 1. 提取参数
///         BatchExecutionParams exec = request.executionParams();
///         ProvenanceConfig config = context.config();
/// 
///         // 2. 调用 PubMed API
///         ESearchResponse searchResponse = pubMedClient.esearch(buildSearchRequest(exec), config);
///         List<String> pmids = extractPmids(searchResponse);
/// 
///         // 3. 获取出版物详情
///         EFetchResponse fetchResponse = pubMedClient.efetch(buildFetchRequest(pmids), config);
/// 
///         // 4. 转换为标准格式
///         List<CanonicalPublication> publications = fetchResponse.articles().stream()
///             .map(converter::toCanonicalPublication)
///             .collect(Collectors.toList());
/// 
///         // 5. 返回结果
///         return ProcessResult.success(publications, extractNextCursor(searchResponse));
/// 
///     @Override
///     public ValidationResult validate(CanonicalPublication data) {
///         List<String> errors = new ArrayList<>();
///         if (data.getTitle() == null || data.getTitle().isBlank()) {
///             errors.add("标题不能为空");
///         if (data.getIdentifiers() == null || data.getIdentifiers().isEmpty()) {
///             errors.add("标识符不能为空");
///         return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
/// 
///     @Override
///     public CanonicalPublication transform(Object rawData) {
///         if (!(rawData instanceof PubmedPublication)) {
///             throw new IllegalArgumentException("不支持的数据类型");
///         return converter.toCanonicalPublication((PubmedPublication) rawData);
/// ```
/// 
/// ## ProviderContext 使用示例
/// 
/// ```java
/// // 构建上下文
/// ProviderContext context = ProviderContext.builder()
///     .config(provenanceConfig)
///     .client(pubMedClient)
///     .attributes(Map.of("apiKey", "xxx", "timeout", 30000))
///     .build();
/// 
/// // 类型安全地获取客户端
/// PubMedClient client = context.getClient(PubMedClient.class);
/// 
/// // 使用上下文中的配置
/// ProvenanceConfig config = context.config();
/// int timeout = config.http().timeoutReadMillis();
/// ```
/// 
/// ## 最佳实践
/// 
/// - **配置优先** - 始终从 ProviderContext 获取配置，不硬编码
///   - **错误处理** - 使用 ProcessResult.failure() 封装错误，不抛异常
///   - **验证分离** - validate() 方法独立验证，不在 process() 中混合验证逻辑
///   - **转换统一** - transform() 方法负责所有数据转换，确保一致性
///   - **日志记录** - 记录关键步骤（搜索、获取、转换）的性能指标
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.common.processor;
