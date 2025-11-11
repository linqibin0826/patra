/**
 * Provenance 提供者统一契约包。
 *
 * <p>定义 Ingest 引擎与数据源提供者之间的统一接口。提供者实现遵循六边形架构原则，
 * 仅负责数据检索和格式转换，不包含业务逻辑。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义数据源提供者的统一契约 {@link DataSourceProvider}
 *   <li>提供提供者注册和发现机制 {@link ProviderRegistry}
 *   <li>规范提供者请求和响应模型
 *   <li>支持批量执行和元数据传递
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link DataSourceProvider} - 提供者契约接口
 *   <li>{@link ProviderRegistry} - 提供者注册表
 *   <li>{@link ProviderRequest} - 提供者请求载荷
 *   <li>{@link ProviderResult} - 提供者响应结果
 *   <li>{@link BatchExecutionParams} - 批量执行参数
 *   <li>{@link BatchMetadata} - 批次元数据
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><b>单一职责</b> - 提供者实现仅负责数据检索，不处理业务规则
 *   <li><b>开闭原则</b> - 通过实现 {@link DataSourceProvider} 扩展新数据源，无需修改现有代码
 *   <li><b>依赖倒置</b> - Ingest 引擎依赖抽象接口，不依赖具体实现
 * </ul>
 *
 * <h2>提供者实现示例</h2>
 *
 * <pre>{@code
 * @Component
 * public class CustomDataSourceProvider implements DataSourceProvider {
 *
 *     @Override
 *     public String getProvenanceCode() {
 *         return "custom";
 *     }
 *
 *     @Override
 *     public ProviderResult fetchData(ProviderRequest request) {
 *         // 1. 调用外部 API
 *         // 2. 转换为 CanonicalLiterature
 *         // 3. 包装为 ProviderResult 返回
 *         return ProviderResult.success(literatures);
 *     }
 * }
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class IngestOrchestrator {
 *     private final ProviderRegistry registry;
 *
 *     public void ingest(String provenanceCode) {
 *         DataSourceProvider provider = registry.getProvider(provenanceCode);
 *         ProviderRequest request = ProviderRequest.builder()
 *             .provenanceCode(provenanceCode)
 *             .build();
 *         ProviderResult result = provider.fetchData(request);
 *         // 处理结果...
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.common.provider;
