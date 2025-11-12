package com.patra.ingest.domain.port;

import com.patra.common.model.DataType;
import com.patra.common.type.TypeReference;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;

/**
 * 数据源端口(六边形架构 - Domain → Infrastructure)
 *
 * <p><strong>职责</strong>: 定义从外部数据源获取指定类型数据的领域契约。此端口抽象了数据源访问的技术细节，基础设施适配器负责：
 *
 * <ul>
 *   <li>与外部数据源 API 交互(PubMed, EPMC, DOAJ, DrugBank 等)
 *   <li>处理 API 认证、限流、重试
 *   <li>将外部数据模型转换为标准化数据类型
 *   <li>处理分页和游标管理
 *   <li>支持多种数据类型（文献、期刊、药品、引用等）
 * </ul>
 *
 * <p><strong>端口语义</strong>: 此接口是六边形架构中的 <strong>输出端口(Output Port)</strong>，定义在 Domain
 * 层，由基础设施层(Infrastructure)实现，确保领域逻辑与数据源技术解耦。
 *
 * <p><strong>架构特性</strong>：
 * <ul>
 *   <li><strong>泛型方法</strong>：通过 {@link TypeReference} 保持运行时类型信息</li>
 *   <li><strong>类型安全</strong>：编译期类型检查，避免类型转换错误</li>
 *   <li><strong>数据类型标识</strong>：使用 {@link DataType} 枚举明确数据类型</li>
 *   <li><strong>能力查询</strong>：通过 {@link #supports} 和 {@link #getSupportedTypes} 判断数据源能力</li>
 * </ul>
 *
 * <p><strong>使用示例</strong>：
 * <pre>{@code
 * // 示例 1: 获取文献数据
 * TypeReference<CanonicalLiterature> litTypeRef = new TypeReference<>() {};
 * DataFetchResult<CanonicalLiterature> result =
 *     dataSourcePort.fetchData(context, DataType.LITERATURE, litTypeRef, batch);
 *
 * // 示例 2: 获取期刊数据
 * TypeReference<Journal> journalTypeRef = new TypeReference<>() {};
 * DataFetchResult<Journal> journals =
 *     dataSourcePort.fetchData(context, DataType.JOURNAL, journalTypeRef, batch);
 *
 * // 示例 3: 检查数据源能力
 * if (dataSourcePort.supports("pubmed", DataType.LITERATURE)) {
 *     // PubMed 支持文献数据
 * }
 *
 * // 示例 4: 查询支持的数据类型
 * Set<DataType> supportedTypes = dataSourcePort.getSupportedTypes("doaj");
 * // 返回 [JOURNAL]
 * }</pre>
 *
 * <p><strong>与框架层的关系</strong>: {@code patra-starter-provenance} 提供的 {@code DataSourceAdapter}
 * 是框架层的技术支撑，而本接口是领域层的业务契约。基础设施层的适配器实现本接口，可以内部使用框架提供的 {@code DataSourceAdapter} 作为技术实现手段。
 *
 * @see com.patra.ingest.domain.model.vo.batch.Batch 批次定义
 * @see com.patra.ingest.domain.model.vo.execution.ExecutionContext 执行上下文
 * @see com.patra.common.model.DataType 数据类型枚举
 * @see com.patra.common.type.TypeReference 类型引用工具
 * @author linqibin
 * @since 0.1.0
 */
public interface DataSourcePort {

  /**
   * 从数据源获取指定类型的数据
   *
   * <p><strong>业务含义</strong>: 根据执行上下文、数据类型标识和批次定义，从外部数据源获取指定类型的标准化数据。
   *
   * <p><strong>执行流程</strong>：
   *
   * <ol>
   *   <li>从执行上下文中提取配置和查询信息
   *   <li>验证数据类型与 TypeReference 的一致性
   *   <li>根据批次定义构建数据源 API 请求
   *   <li>调用外部数据源 API 获取原始数据
   *   <li>将原始数据转换为指定类型的标准化模型
   *   <li>返回结果，包含数据列表、游标和错误信息
   * </ol>
   *
   * <p><strong>类型安全</strong>：
   * <ul>
   *   <li>编译期类型检查：返回的 {@code List<T>} 保证类型安全</li>
   *   <li>运行时类型验证：通过 {@link TypeReference} 保持泛型信息</li>
   *   <li>类型一致性：{@code dataType} 必须与 {@code typeRef} 的类型匹配</li>
   * </ul>
   *
   * <p><strong>错误处理</strong>: 实现应捕获所有异常，将其转换为 {@link DataFetchResult} 返回，
   * 并正确设置 {@link ErrorType} 以指导上层的重试策略。
   *
   * <p><strong>使用示例</strong>：
   * <pre>{@code
   * // 获取文献数据
   * TypeReference<CanonicalLiterature> litRef = new TypeReference<>() {};
   * DataFetchResult<CanonicalLiterature> result =
   *     dataSourcePort.fetchData(context, DataType.LITERATURE, litRef, batch);
   *
   * // 获取期刊数据
   * TypeReference<Journal> journalRef = new TypeReference<>() {};
   * DataFetchResult<Journal> journals =
   *     dataSourcePort.fetchData(context, DataType.JOURNAL, journalRef, batch);
   * }</pre>
   *
   * @param <T> 数据类型（通过 TypeReference 保持运行时泛型信息）
   * @param context 执行上下文，包含配置快照、查询条件和编译参数
   * @param dataType 数据类型标识（如 LITERATURE、JOURNAL、DRUG）
   * @param typeRef 类型引用（用于保持运行时泛型信息）
   * @param batch 批次定义，包含批次编号、分页参数和游标令牌
   * @return 数据获取结果，包含指定类型的数据列表、游标和错误信息
   * @throws IllegalArgumentException 如果 dataType 与 typeRef 不一致
   */
  <T> DataFetchResult<T> fetchData(
      ExecutionContext context, DataType dataType, TypeReference<T> typeRef, Batch batch);

  /**
   * 判断是否支持指定的数据源和数据类型组合
   *
   * <p><strong>应用场景</strong>：
   * <ul>
   *   <li>路由决策：根据数据源和数据类型选择合适的适配器</li>
   *   <li>能力发现：在运行时查询数据源的支持能力</li>
   *   <li>配置验证：校验数据源配置是否支持所需的数据类型</li>
   * </ul>
   *
   * <p><strong>使用示例</strong>：
   * <pre>{@code
   * // 检查 PubMed 是否支持文献数据
   * if (dataSourcePort.supports("pubmed", DataType.LITERATURE)) {
   *     // 支持，可以获取文献数据
   * }
   *
   * // 检查 DOAJ 是否支持药品数据
   * if (!dataSourcePort.supports("doaj", DataType.DRUG)) {
   *     // 不支持，需要选择其他数据源
   * }
   * }</pre>
   *
   * @param provenanceCode 数据源代码（如 "pubmed"、"doaj"、"drugbank"）
   * @param dataType 数据类型标识
   * @return 如果支持则返回 true，否则返回 false
   */
  boolean supports(String provenanceCode, DataType dataType);

  /**
   * 获取指定数据源支持的所有数据类型
   *
   * <p><strong>应用场景</strong>：
   * <ul>
   *   <li>能力发现：查询数据源支持的所有数据类型</li>
   *   <li>动态配置：根据数据源能力动态调整采集策略</li>
   *   <li>监控展示：在管理界面展示数据源的支持能力</li>
   * </ul>
   *
   * <p><strong>返回值约定</strong>：
   * <ul>
   *   <li>如果数据源存在且支持多种类型，返回包含所有类型的不可变集合</li>
   *   <li>如果数据源不存在或不支持任何类型，返回空的不可变集合（不返回 null）</li>
   * </ul>
   *
   * <p><strong>使用示例</strong>：
   * <pre>{@code
   * // 查询 PubMed 支持的数据类型
   * Set<DataType> pubmedTypes = dataSourcePort.getSupportedTypes("pubmed");
   * // 返回：[LITERATURE, CITATION]
   *
   * // 查询未知数据源
   * Set<DataType> unknownTypes = dataSourcePort.getSupportedTypes("unknown");
   * // 返回：[]（空集合）
   *
   * // 遍历支持的类型
   * pubmedTypes.forEach(type -> {
   *     System.out.println("支持数据类型: " + type.getDescription());
   * });
   * }</pre>
   *
   * @param provenanceCode 数据源代码
   * @return 支持的数据类型集合（不可变），如果数据源不存在则返回空集合
   */
  Set<DataType> getSupportedTypes(String provenanceCode);

  /**
   * 数据获取结果值对象
   *
   * <p>封装数据源获取操作的执行结果，包括成功状态、数据载荷、数据类型标识、分页游标、错误信息和扩展元数据。
   *
   * <p><strong>核心特性</strong>：
   * <ul>
   *   <li><strong>泛型化</strong>：{@code data} 字段改为 {@code List<T>}，支持任意数据类型</li>
   *   <li><strong>数据类型标识</strong>：新增 {@code dataType} 字段，明确数据类型</li>
   *   <li><strong>扩展元数据</strong>：新增 {@code metadata} 字段，支持传递额外信息</li>
   * </ul>
   *
   * <p><strong>使用场景</strong>：
   *
   * <ul>
   *   <li><strong>完全成功</strong>: {@code success=true, data 非空, errorType=NONE}
   *   <li><strong>部分成功</strong>: {@code success=true, errorType=PARTIAL_SUCCESS, errorMessage 包含警告}
   *   <li><strong>可重试失败</strong>: {@code success=false, errorType=RETRIABLE, errorMessage 包含错误详情}
   *   <li><strong>不可重试失败</strong>: {@code success=false, errorType=NON_RETRIABLE, errorMessage 包含错误详情}
   * </ul>
   *
   * <p><strong>使用示例</strong>：
   * <pre>{@code
   * // 成功结果
   * List<CanonicalLiterature> literatures = List.of(lit1, lit2);
   * DataFetchResult<CanonicalLiterature> successResult =
   *     DataFetchResult.success(literatures, DataType.LITERATURE, "cursor123");
   *
   * // 失败结果
   * DataFetchResult<CanonicalLiterature> failureResult =
   *     DataFetchResult.failure(DataType.LITERATURE, "Network timeout", ErrorType.RETRIABLE);
   *
   * // 部分成功结果
   * DataFetchResult<CanonicalLiterature> partialResult =
   *     DataFetchResult.partialSuccess(
   *         literatures, DataType.LITERATURE, "cursor456", "5 records failed validation");
   * }</pre>
   *
   * @param <T> 数据类型
   * @param success 是否成功获取数据(无终止性错误)
   * @param data 数据列表（泛型化，支持任意类型），不可变
   * @param dataType 数据类型标识（如 LITERATURE、JOURNAL）
   * @param nextCursorToken 下一页游标令牌(用于基于游标的分页，可为空)
   * @param errorMessage 错误或警告消息(失败时必填，部分成功时可选)
   * @param fetchedCount 实际获取或尝试的记录数(用于监控指标)
   * @param errorType 错误类型，指导上层重试策略
   * @param metadata 扩展元数据（可选，用于传递额外信息，如 API 版本、限流信息等）
   */
  @Builder
  record DataFetchResult<T>(
      boolean success,
      List<T> data,
      DataType dataType,
      String nextCursorToken,
      String errorMessage,
      int fetchedCount,
      ErrorType errorType,
      Map<String, Object> metadata) {

    /**
     * Compact 构造函数，确保不变式
     *
     * @param success 是否成功
     * @param data 数据列表
     * @param dataType 数据类型标识
     * @param nextCursorToken 游标令牌
     * @param errorMessage 错误消息
     * @param fetchedCount 获取数量
     * @param errorType 错误类型
     * @param metadata 扩展元数据
     */
    public DataFetchResult {
      // 确保 data 不为 null，转换为不可变列表
      data = data == null ? List.of() : List.copyOf(data);

      // 确保 fetchedCount 不小于实际数据大小
      fetchedCount = Math.max(fetchedCount, data.size());

      // 确保 errorType 不为 null，默认为 NONE
      errorType = errorType == null ? ErrorType.NONE : errorType;

      // 确保 metadata 不为 null（可以是空 Map）
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * 创建成功结果（简化工厂方法）
     *
     * <p><strong>适用场景</strong>：数据获取完全成功，无任何警告或错误
     *
     * <p><strong>使用示例</strong>：
     * <pre>{@code
     * List<CanonicalLiterature> literatures = List.of(lit1, lit2, lit3);
     * DataFetchResult<CanonicalLiterature> result =
     *     DataFetchResult.success(literatures, DataType.LITERATURE, "cursor123");
     * }</pre>
     *
     * @param <T> 数据类型
     * @param data 获取到的数据列表
     * @param dataType 数据类型标识
     * @param nextCursorToken 下一页游标令牌（如果没有更多数据则为 null）
     * @return 不可变的成功结果
     */
    public static <T> DataFetchResult<T> success(
        List<T> data, DataType dataType, String nextCursorToken) {
      return DataFetchResult.<T>builder()
          .success(true)
          .data(data)
          .dataType(dataType)
          .nextCursorToken(nextCursorToken)
          .fetchedCount(data == null ? 0 : data.size())
          .errorType(ErrorType.NONE)
          .build();
    }

    /**
     * 创建失败结果（通用工厂方法）
     *
     * <p><strong>适用场景</strong>：数据获取失败，需要明确错误类型和错误消息
     *
     * <p><strong>使用示例</strong>：
     * <pre>{@code
     * // 可重试错误（网络超时）
     * DataFetchResult<CanonicalLiterature> retriableFailure =
     *     DataFetchResult.failure(
     *         DataType.LITERATURE,
     *         "Network timeout after 30s",
     *         ErrorType.RETRIABLE);
     *
     * // 不可重试错误（认证失败）
     * DataFetchResult<CanonicalLiterature> nonRetriableFailure =
     *     DataFetchResult.failure(
     *         DataType.LITERATURE,
     *         "Invalid API key",
     *         ErrorType.NON_RETRIABLE);
     * }</pre>
     *
     * @param <T> 数据类型
     * @param dataType 数据类型标识
     * @param errorMessage 诊断消息
     * @param errorType 错误类型（RETRIABLE 或 NON_RETRIABLE）
     * @return 失败结果
     */
    public static <T> DataFetchResult<T> failure(
        DataType dataType, String errorMessage, ErrorType errorType) {
      return DataFetchResult.<T>builder()
          .success(false)
          .dataType(dataType)
          .errorMessage(errorMessage)
          .errorType(errorType)
          .fetchedCount(0)
          .build();
    }

    /**
     * 创建部分成功结果
     *
     * <p><strong>适用场景</strong>：某些数据获取成功，但部分记录处理失败或有警告
     *
     * <p><strong>使用示例</strong>：
     * <pre>{@code
     * // 100 条记录中有 95 条成功，5 条验证失败
     * List<CanonicalLiterature> successLiteratures = List.of(lit1, lit2, ...); // 95 条
     * DataFetchResult<CanonicalLiterature> partialResult =
     *     DataFetchResult.partialSuccess(
     *         successLiteratures,
     *         DataType.LITERATURE,
     *         "cursor456",
     *         "5 records failed validation: missing required fields");
     * }</pre>
     *
     * @param <T> 数据类型
     * @param data 成功转换的数据列表
     * @param dataType 数据类型标识
     * @param nextCursorToken 继续分页的游标令牌
     * @param warningMessage 警告详情
     * @return 部分成功结果
     */
    public static <T> DataFetchResult<T> partialSuccess(
        List<T> data, DataType dataType, String nextCursorToken, String warningMessage) {
      return DataFetchResult.<T>builder()
          .success(true)
          .data(data)
          .dataType(dataType)
          .nextCursorToken(nextCursorToken)
          .errorMessage(warningMessage)
          .fetchedCount(data == null ? 0 : data.size())
          .errorType(ErrorType.PARTIAL_SUCCESS)
          .build();
    }

    /**
     * 错误类型枚举
     *
     * <p>指导应用层的重试和失败处理策略。
     *
     * <p><strong>使用指南</strong>：
     * <ul>
     *   <li><strong>NONE</strong>：操作完全成功，无错误</li>
     *   <li><strong>RETRIABLE</strong>：瞬时错误，建议重试（如网络超时、限流）</li>
     *   <li><strong>NON_RETRIABLE</strong>：终止性错误，不应重试（如认证失败、参数错误）</li>
     *   <li><strong>PARTIAL_SUCCESS</strong>：部分成功，记录警告并继续（如部分记录失败）</li>
     * </ul>
     */
    public enum ErrorType {
      /** 无错误，操作完全成功 */
      NONE,

      /**
       * 瞬时错误，调用方应遵守退避规则重试
       *
       * <p>示例: 网络超时、HTTP 429(限流)、HTTP 503(服务不可用)
       */
      RETRIABLE,

      /**
       * 终止性错误，调用方不应自动重试
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
