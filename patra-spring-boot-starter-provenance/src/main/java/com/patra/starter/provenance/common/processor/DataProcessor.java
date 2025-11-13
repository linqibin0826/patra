package com.patra.starter.provenance.common.processor;

import com.patra.common.model.DataType;
import com.patra.starter.provenance.common.provider.ProviderRequest;

/**
 * 数据处理器策略接口
 *
 * <p>DataProcessor是多数据类型架构的核心策略接口，每个数据类型（LITERATURE、JOURNAL、DRUG等）
 * 都有对应的Processor实现类。
 *
 * <p><strong>架构角色</strong>：
 * <ul>
 *   <li>位于Framework层（patra-spring-boot-starter-provenance）</li>
 *   <li>被DataSourceProvider委托调用</li>
 *   <li>通过ProcessorRegistry自动注册和查找</li>
 * </ul>
 *
 * <p><strong>职责</strong>：
 * <ul>
 *   <li>处理特定数据类型的数据获取流程（ESearch/EFetch等）</li>
 *   <li>转换原始数据为领域模型（如PubmedLiterature → CanonicalLiterature）</li>
 *   <li>验证数据完整性和正确性</li>
 * </ul>
 *
 * <p><strong>使用示例</strong>：
 * <pre>{@code
 * // 实现Processor
 * @Component
 * public class PubmedLiteratureProcessor implements DataProcessor<CanonicalLiterature> {
 *     @Override
 *     public DataType getDataType() {
 *         return DataType.LITERATURE;
 *     }
 *
 *     @Override
 *     public ProcessResult<CanonicalLiterature> process(
 *             ProviderRequest request,
 *             ProviderContext context) {
 *         // 1. ESearch
 *         // 2. EFetch
 *         // 3. Convert
 *         // 4. Validate
 *         return ProcessResult.success(literatures, nextCursor);
 *     }
 * }
 *
 * // Provider委托Processor
 * public class PubmedDataSourceProvider implements DataSourceProvider {
 *     private final DataProcessor<CanonicalLiterature> literatureProcessor;
 *
 *     public <T> ProviderResult<T> fetchData(...) {
 *         ProcessResult<T> result = processor.process(request, context);
 *         // 转换为ProviderResult
 *     }
 * }
 * }</pre>
 *
 * @param <T> 处理的数据类型（如CanonicalLiterature、Journal、Drug）
 * @author Patra Architecture Team
 * @since 0.1.0
 */
public interface DataProcessor<T> {

    /**
     * 获取此Processor处理的数据类型
     *
     * @return 数据类型标识
     */
    DataType getDataType();

    /**
     * 判断是否支持指定的数据类型
     *
     * <p>默认实现：比较数据类型是否相等
     *
     * @param dataType 数据类型
     * @return 如果支持则返回true
     */
    default boolean supports(DataType dataType) {
        return getDataType().equals(dataType);
    }

    /**
     * 处理数据获取请求
     *
     * <p>这是Processor的核心方法，完成以下流程：
     * <ol>
     *   <li>从外部API获取原始数据（如PubMed ESearch/EFetch）</li>
     *   <li>转换原始数据为领域模型（调用transform方法）</li>
     *   <li>验证数据完整性（调用validate方法）</li>
     *   <li>返回处理结果</li>
     * </ol>
     *
     * <p><strong>错误处理策略</strong>：
     * <ul>
     *   <li>网络错误、超时：返回FAILED状态，由上层重试</li>
     *   <li>部分数据转换失败：返回PARTIAL_SUCCESS状态</li>
     *   <li>验证失败：返回VALIDATION_ERROR状态</li>
     * </ul>
     *
     * @param request 请求参数（包含查询条件、分页信息等）
     * @param context 上下文信息（包含客户端、配置等）
     * @return 处理结果
     */
    ProcessResult<T> process(ProviderRequest request, ProviderContext context);

    /**
     * 验证数据完整性和正确性
     *
     * <p>检查必填字段、数据格式、业务规则等。
     *
     * <p><strong>验证规则示例</strong>：
     * <ul>
     *   <li>文献数据：PMID、标题、作者不能为空</li>
     *   <li>期刊数据：ISSN、期刊名不能为空</li>
     *   <li>药品数据：DrugBank ID、药品名不能为空</li>
     * </ul>
     *
     * @param data 待验证的数据
     * @return 验证结果
     */
    ValidationResult validate(T data);

    /**
     * 转换原始数据为目标类型
     *
     * <p>将外部API返回的原始数据（如PubmedLiterature、DoajJournal）
     * 转换为领域模型（如CanonicalLiterature、Journal）。
     *
     * @param rawData 原始数据
     * @return 转换后的领域模型
     * @throws TransformationException 如果转换失败
     */
    T transform(Object rawData) throws TransformationException;
}
