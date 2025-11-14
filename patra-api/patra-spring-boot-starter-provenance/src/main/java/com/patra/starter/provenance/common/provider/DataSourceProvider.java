package com.patra.starter.provenance.common.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.model.DataType;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.processor.DataProcessor;
import com.patra.starter.provenance.internal.metadata.PlanMetadata;
import java.util.Optional;
import java.util.Set;

/**
 * 数据源提供者统一契约接口
 *
 * <p>DataSourceProvider是Framework层的核心抽象，每个外部数据源（PubMed、DOAJ、Crossref等） 都有对应的Provider实现类。
 *
 * <p><strong>核心特性</strong>：
 *
 * <ul>
 *   <li>支持一对多：一个Provider可以支持多个DataType
 *   <li>泛型化：fetchData方法支持泛型返回类型
 *   <li>委托模式：Provider可以委托给DataProcessor处理
 *   <li>能力查询：getSupportedDataTypes()和supports()方法
 * </ul>
 *
 * <p><strong>架构角色</strong>：
 *
 * <ul>
 *   <li>位于Framework层（patra-spring-boot-starter-provenance）
 *   <li>被DataSourceAdapter（Infrastructure层）调用
 *   <li>委托给DataProcessor（Framework层）处理
 * </ul>
 *
 * <p>提供者职责说明：
 *
 * <ul>
 *   <li>仅负责数据检索和格式转换，不包含业务逻辑
 *   <li>操作类型（HARVEST、UPDATE 等）是编排层关注点，由上层处理
 *   <li>通过 {@link ProviderRegistry} 进行注册和发现
 * </ul>
 *
 * <p><strong>使用示例</strong>：
 *
 * <pre>{@code
 * // 实现Provider（支持多种数据类型）
 * @Component
 * public class PubmedDataSourceProvider implements DataSourceProvider {
 *     private static final Set<DataType> SUPPORTED_TYPES = Set.of(
 *         DataType.LITERATURE,
 *         DataType.CITATION,
 *         DataType.AUTHOR
 *     );
 *
 *     @Override
 *     public String getProvenanceCode() {
 *         return "pubmed";
 *     }
 *
 *     @Override
 *     public Set<DataType> getSupportedDataTypes() {
 *         return SUPPORTED_TYPES;
 *     }
 *
 *     @Override
 *     public <T> ProviderResult<T> fetchData(
 *             ProviderRequest request,
 *             DataType dataType,
 *             Class<T> targetClass) {
 *         // 委托给对应的Processor
 *         DataProcessor<T> processor = getProcessor(dataType);
 *         ProcessResult<T> result = processor.process(request, buildContext());
 *         return convertToProviderResult(result);
 *     }
 * }
 * }</pre>
 *
 * <p><b>注意</b>：此接口位于 starter 包中，属于框架层抽象。 如果需要在领域层定义端口（Port），请在对应的 domain 模块中定义。
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
public interface DataSourceProvider {

  /**
   * 返回此提供者服务的数据源代码（唯一标识）
   *
   * <p>数据源代码用于标识外部数据源，例如：
   *
   * <ul>
   *   <li>"pubmed" - PubMed数据库
   *   <li>"doaj" - Directory of Open Access Journals
   *   <li>"crossref" - Crossref引用数据库
   *   <li>"drugbank" - DrugBank药品数据库
   * </ul>
   *
   * @return 唯一的数据源代码（小写，不含空格）
   */
  String getProvenanceCode();

  /**
   * 获取此Provider支持的所有数据类型
   *
   * <p>核心特性：一个Provider可以支持多个DataType。例如：
   *
   * <ul>
   *   <li>PubMed支持：LITERATURE、CITATION、AUTHOR
   *   <li>DOAJ支持：JOURNAL
   *   <li>DrugBank支持：DRUG、DRUG_INTERACTION
   * </ul>
   *
   * @return 支持的数据类型集合（不可变）
   */
  Set<DataType> getSupportedDataTypes();

  /**
   * 判断是否支持指定的数据类型
   *
   * <p>默认实现:检查getSupportedDataTypes()是否包含指定类型
   *
   * @param dataType 数据类型
   * @return 如果支持则返回true
   */
  default boolean supports(DataType dataType) {
    return getSupportedDataTypes().contains(dataType);
  }

  /**
   * 准备计划元数据
   *
   * <p>调用外部数据源 API 获取计划所需的元数据。
   *
   * <p>注意:此方法只接收通用的查询参数,不接收 Ingest 特定的 ExecutionContext
   *
   * @param query 查询字符串
   * @param params 查询参数(JSON 格式,可包含分页、排序等)
   * @param config 数据源配置
   * @return 计划元数据(使用继承体系支持不同数据源)
   * @throws ProvenanceClientException 数据源访问失败时抛出
   */
  default PlanMetadata preparePlan(String query, JsonNode params, ProvenanceConfig config) {
    throw new UnsupportedOperationException(
        "Provider " + getProvenanceCode() + " 不支持 preparePlan 操作");
  }

  /**
   * 从数据源获取指定类型的数据
   *
   * <p><strong>实现策略</strong>：
   *
   * <ol>
   *   <li>验证数据类型是否支持
   *   <li>委托给对应的DataProcessor处理
   *   <li>转换ProcessResult为ProviderResult
   *   <li>处理错误和异常
   * </ol>
   *
   * @param <T> 数据类型
   * @param request 请求参数
   * @param dataType 数据类型标识
   * @param targetClass 目标类型（用于类型安全）
   * @return Provider结果
   */
  <T> ProviderResult<T> fetchData(ProviderRequest request, DataType dataType, Class<T> targetClass);

  /**
   * 获取指定数据类型的Processor（可选方法）
   *
   * <p>此方法为可选实现，用于暴露内部的DataProcessor。 大多数情况下，Provider内部使用Processor，不需要暴露给外部。
   *
   * @param dataType 数据类型
   * @return Processor实例（如果存在）
   */
  default Optional<DataProcessor<?>> getProcessor(DataType dataType) {
    return Optional.empty();
  }
}
