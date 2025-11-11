package com.patra.starter.provenance.common.provider;

/**
 * 数据源提供者统一契约接口
 *
 * <p>本接口作为 Provenance Starter 提供的提供者框架，定义了数据源提供者的统一规范。
 *
 * <p>Ingest 引擎通过此接口与各数据源交互，实现新数据源只需提供新的提供者实现，无需修改现有采集逻辑。
 *
 * <p>提供者职责说明：
 *
 * <ul>
 *   <li>仅负责数据检索和格式转换，不包含业务逻辑
 *   <li>操作类型（HARVEST、UPDATE 等）是编排层关注点，由上层处理
 *   <li>通过 {@link ProviderRegistry} 进行注册和发现
 * </ul>
 *
 * <p><b>注意</b>：此接口位于 starter 包中，属于框架层抽象。
 * 如果需要在领域层定义端口（Port），请在对应的 domain 模块中定义。
 */
public interface DataSourceProvider {

  /**
   * 返回此提供者服务的数据源代码
   *
   * @return 唯一的数据源代码（如 {@code pubmed}、{@code epmc}）
   */
  String getProvenanceCode();

  /**
   * 执行数据检索和转换工作流
   *
   * @param request 来自 Ingest 引擎的不可变请求载荷
   * @return 描述结果、载荷和重试指导的结果对象
   */
  ProviderResult fetchData(ProviderRequest request);
}
