package com.patra.starter.provenance.common.adapter;

/**
 * 数据源适配器统一契约接口
 *
 * <p>Ingest 引擎仅依赖此接口,通过提供新的实现即可引入新的数据源,无需修改现有的采集逻辑。
 *
 * <p>适配器职责说明:
 *
 * <ul>
 *   <li>仅负责数据检索和格式转换,不包含业务逻辑
 *   <li>操作类型(HARVEST、UPDATE 等)是编排层关注点,由上层处理
 *   <li>通过 {@link AdapterRegistry} 进行注册和发现
 * </ul>
 */
public interface DataSourceAdapter {

  /**
   * 返回此适配器服务的数据源代码
   *
   * @return 唯一的数据源代码(如 {@code pubmed}、{@code epmc})
   */
  String getProvenanceCode();

  /**
   * 执行数据检索和转换工作流
   *
   * @param request 来自 Ingest 引擎的不可变请求载荷
   * @return 描述结果、载荷和重试指导的结果对象
   */
  AdapterResult fetchData(AdapterRequest request);
}
