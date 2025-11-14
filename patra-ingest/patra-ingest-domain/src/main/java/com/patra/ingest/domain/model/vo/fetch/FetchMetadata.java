package com.patra.ingest.domain.model.vo.fetch;

import java.util.Map;
import java.util.Optional;

/**
 * 数据源抓取元数据 - Ingest 领域模型
 *
 * <p>封装从外部数据源获取的元数据信息，用于批次生成规划。
 *
 * <p><strong>职责</strong>：
 *
 * <ul>
 *   <li>提供总记录数，用于计算批次数量
 *   <li>提供数据源标识，用于策略路由
 *   <li>提供状态令牌（opaque），用于跨批次传递上下文
 * </ul>
 *
 * <p><strong>设计原则</strong>：
 *
 * <ul>
 *   <li>与外部数据源实现解耦
 *   <li>不包含框架或技术细节
 *   <li>状态令牌为 opaque 类型，Ingest 不解析其内容
 * </ul>
 *
 * <p><strong>使用场景</strong>：
 *
 * <ul>
 *   <li>DataSourcePort.prepareFetchMetadata() 返回此接口
 *   <li>BatchGenerationStrategy.generateBatches() 接收此接口作为输入
 *   <li>FetchMetadataTranslator 将外部元数据翻译为此接口
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.3.0
 */
public interface FetchMetadata {

  /**
   * 获取总记录数
   *
   * <p>用于批次生成器计算需要生成的批次数量
   *
   * @return 总记录数（>= 0）
   */
  int totalRecords();

  /**
   * 获取数据源代码
   *
   * <p>用于批次生成策略的路由选择
   *
   * @return 数据源代码（如 "pubmed", "doaj", "epmc"）
   */
  String dataSourceCode();

  /**
   * 检查是否包含状态令牌
   *
   * <p>某些数据源（如 PubMed）在计划阶段会返回状态令牌（如 WebEnv）， 执行阶段可以使用该令牌避免重复查询。
   *
   * @return 如果包含状态令牌则返回 true
   */
  boolean hasStateToken();

  /**
   * 获取状态令牌
   *
   * <p>状态令牌是 opaque 的，Ingest 不解析其内容，只负责在批次间传递。 具体的令牌格式由数据源定义（如 PubMed 的 webEnv + queryKey）。
   *
   * @return 状态令牌的键值对（如果存在）
   */
  Optional<Map<String, String>> stateToken();

  /**
   * 创建空的抓取元数据（表示无可用数据）
   *
   * @param dataSourceCode 数据源代码
   * @return 空抓取元数据
   */
  static FetchMetadata empty(String dataSourceCode) {
    return new EmptyFetchMetadata(dataSourceCode);
  }
}

/** 空抓取元数据实现（包级私有） */
record EmptyFetchMetadata(String dataSourceCode) implements FetchMetadata {

  @Override
  public int totalRecords() {
    return 0;
  }

  @Override
  public boolean hasStateToken() {
    return false;
  }

  @Override
  public Optional<Map<String, String>> stateToken() {
    return Optional.empty();
  }
}
