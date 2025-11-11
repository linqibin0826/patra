package com.patra.ingest.domain.port;

import com.patra.common.model.CanonicalLiterature;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import java.util.List;
import lombok.Builder;

/**
 * 数据源端口(六边形架构 - Domain → Infrastructure)
 *
 * <p><b>职责</b>: 定义从外部数据源获取文献数据的领域契约。此端口抽象了数据源访问的技术细节, 基础设施适配器负责:
 *
 * <ul>
 *   <li>与外部数据源 API 交互(PubMed, EPMC 等)
 *   <li>处理 API 认证、限流、重试
 *   <li>将外部数据模型转换为标准化文献
 *   <li>处理分页和游标管理
 * </ul>
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>输出端口(Output Port)</b>,定义在 Domain
 * 层,由基础设施层(Infrastructure)实现,确保领域逻辑与数据源技术解耦。
 *
 * <p><b>与框架层的关系</b>: {@code patra-starter-provenance} 提供的 {@code DataSourceAdapter}
 * 是框架层的技术支撑,而本接口是领域层的业务契约。基础设施层的适配器实现本接口, 可以内部使用框架提供的 {@code DataSourceAdapter} 作为技术实现手段。
 *
 * @see com.patra.ingest.domain.model.vo.batch.Batch 批次定义
 * @see com.patra.ingest.domain.model.vo.execution.ExecutionContext 执行上下文
 * @author linqibin
 * @since 0.1.0
 */
public interface DataSourcePort {

  /**
   * 从数据源获取文献数据
   *
   * <p><b>业务含义</b>: 根据执行上下文和批次定义,从外部数据源获取标准化的文献数据。
   *
   * <p><b>执行流程</b>:
   *
   * <ol>
   *   <li>从执行上下文中提取配置和查询信息
   *   <li>根据批次定义构建数据源 API 请求
   *   <li>调用外部数据源 API 获取原始数据
   *   <li>将原始数据转换为标准化文献模型
   *   <li>返回结果,包含文献列表、游标和错误信息
   * </ol>
   *
   * <p><b>错误处理</b>: 实现应捕获所有异常,将其转换为 {@link DataFetchResult} 返回, 并正确设置 {@link
   * DataFetchResult.ErrorType} 以指导上层的重试策略。
   *
   * @param context 执行上下文,包含配置快照、查询条件和编译参数
   * @param batch 批次定义,包含批次编号、分页参数和游标令牌
   * @return 数据获取结果,包含文献列表、游标和错误信息
   */
  DataFetchResult fetchData(ExecutionContext context, Batch batch);

  /**
   * 数据获取结果值对象
   *
   * <p>封装数据源获取操作的执行结果,包括成功状态、数据载荷、分页游标和错误信息。
   *
   * <p><b>使用场景</b>:
   *
   * <ul>
   *   <li><b>完全成功</b>: {@code success=true, literatures 非空, errorType=NONE}
   *   <li><b>部分成功</b>: {@code success=true, errorType=PARTIAL_SUCCESS, errorMessage 包含警告}
   *   <li><b>可重试失败</b>: {@code success=false, errorType=RETRIABLE, errorMessage 包含错误详情}
   *   <li><b>不可重试失败</b>: {@code success=false, errorType=NON_RETRIABLE, errorMessage 包含错误详情}
   * </ul>
   *
   * @param success 是否成功获取数据(无终止性错误)
   * @param literatures 标准化文献列表(不可变)
   * @param nextCursorToken 下一页游标令牌(用于基于游标的分页,可为空)
   * @param errorMessage 错误或警告消息(失败时必填,部分成功时可选)
   * @param fetchedCount 实际获取或尝试的记录数(用于监控指标)
   * @param errorType 错误类型,指导上层重试策略
   */
  @Builder
  record DataFetchResult(
      boolean success,
      List<CanonicalLiterature> literatures,
      String nextCursorToken,
      String errorMessage,
      int fetchedCount,
      ErrorType errorType) {

    /**
     * Compact 构造函数,确保不变式
     *
     * @param success 是否成功
     * @param literatures 文献列表
     * @param nextCursorToken 游标令牌
     * @param errorMessage 错误消息
     * @param fetchedCount 获取数量
     * @param errorType 错误类型
     */
    public DataFetchResult {
      literatures = literatures == null ? List.of() : List.copyOf(literatures);
      fetchedCount = Math.max(fetchedCount, literatures.size());
      errorType = errorType == null ? ErrorType.NONE : errorType;
    }

    /**
     * 创建成功结果
     *
     * @param literatures 获取到的文献列表
     * @param nextCursorToken 下一页游标令牌
     * @return 不可变的成功结果
     */
    public static DataFetchResult success(
        List<CanonicalLiterature> literatures, String nextCursorToken) {
      return new DataFetchResult(
          true,
          literatures,
          nextCursorToken,
          null,
          literatures == null ? 0 : literatures.size(),
          ErrorType.NONE);
    }

    /**
     * 创建可重试的失败结果
     *
     * <p>用于瞬时错误场景(网络超时、限流、临时不可用等),通知调用方可以尝试重试。
     *
     * @param errorMessage 诊断消息
     * @return 可重试的失败结果
     */
    public static DataFetchResult retriableFailure(String errorMessage) {
      return new DataFetchResult(false, List.of(), null, errorMessage, 0, ErrorType.RETRIABLE);
    }

    /**
     * 创建不可重试的失败结果
     *
     * <p>用于终止性错误场景(认证失败、参数错误、资源不存在等),通知调用方不应重试。
     *
     * @param errorMessage 诊断消息
     * @return 终止性失败结果
     */
    public static DataFetchResult nonRetriableFailure(String errorMessage) {
      return new DataFetchResult(
          false, List.of(), null, errorMessage, 0, ErrorType.NON_RETRIABLE);
    }

    /**
     * 创建部分成功结果
     *
     * <p>用于某些项目成功但需要记录警告的场景(部分记录转换失败、数据质量问题等)。
     *
     * @param literatures 成功转换的文献列表
     * @param nextCursorToken 继续分页的游标令牌
     * @param warningMessage 警告详情
     * @param totalAttempted 处理的总项目数(包括失败项)
     * @return 部分成功结果
     */
    public static DataFetchResult partialSuccess(
        List<CanonicalLiterature> literatures,
        String nextCursorToken,
        String warningMessage,
        int totalAttempted) {
      return new DataFetchResult(
          true,
          literatures,
          nextCursorToken,
          warningMessage,
          totalAttempted,
          ErrorType.PARTIAL_SUCCESS);
    }

    /**
     * 指示失败是否可重试
     *
     * @return 如果适配器建议重试操作则返回 true
     */
    public boolean isRetriable() {
      return errorType == ErrorType.RETRIABLE;
    }

    /**
     * 错误类型枚举
     *
     * <p>指导应用层的重试和失败处理策略。
     */
    public enum ErrorType {
      /** 无错误,操作完全成功 */
      NONE,

      /**
       * 瞬时错误,调用方应遵守退避规则重试
       *
       * <p>示例: 网络超时、HTTP 429(限流)、HTTP 503(服务不可用)
       */
      RETRIABLE,

      /**
       * 终止性错误,调用方不应自动重试
       *
       * <p>示例: HTTP 401(认证失败)、HTTP 400(参数错误)、HTTP 404(资源不存在)
       */
      NON_RETRIABLE,

      /**
       * 部分成功并带有警告
       *
       * <p>示例: 批量处理中部分记录失败、数据质量警告
       *
       * <p>调用方应记录警告日志并继续处理
       */
      PARTIAL_SUCCESS
    }
  }
}
