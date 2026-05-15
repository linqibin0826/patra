package com.patra.registry.domain.port;

import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/// 数据源配置仓储接口,负责数据源配置聚合根的持久化和重建。
///
/// **职责**:
///
/// - 保存和查询数据源元数据及其关联的运营配置
///   - 根据业务标识(数据源代码)查询数据源
///   - 根据时态查询条件检索特定时刻有效的配置
///   - 组装完整的数据源配置聚合根
///
/// **时态查询支持**:所有`findActive*`方法接受`at`参数, 查询指定时刻有效的配置,支持配置的安全更新、审计和回溯查询。
///
/// **注意**:所有方法使用领域语言表达业务意图,隐藏底层持久化技术细节(SQL/NoSQL)。
///
/// @author linqibin
/// @since 0.1.0
public interface ProvenanceConfigRepository {

  /// 根据数据源代码查询数据源元数据。
  ///
  /// 数据源代码是全局唯一且稳定的业务标识符,如`pubmed`、`crossref`。
  ///
  /// @param provenanceCode 数据源代码,不可为null
  /// @return 包含数据源元数据的Optional,如果未找到则为空
  Optional<Provenance> findProvenanceByCode(ProvenanceCode provenanceCode);

  /// 查询所有已注册的数据源元数据。
  ///
  /// @return 所有数据源的列表,永不为null,可能为空
  List<Provenance> findAllProvenances();

  /// 查询指定时刻有效的时间窗口偏移配置。
  ///
  /// 支持作用域优先级:TASK级 > OPERATION级 > SOURCE级。
  ///
  /// @param provenanceId 数据源ID,不可为null
  /// @param operationType 操作类型(`HARVEST/UPDATE/BACKFILL`),null表示查询全局配置
  /// @param at 查询时刻,用于时态过滤,不可为null
  /// @return 包含时间窗口偏移配置的Optional,如果未找到则为空
  Optional<WindowOffsetConfig> findActiveWindowOffset(
      Long provenanceId, String operationType, Instant at);

  /// 查询指定时刻有效的分页配置。
  ///
  /// 支持端点级配置覆盖数据源级配置。
  ///
  /// @param provenanceId 数据源ID,不可为null
  /// @param operationType 操作类型(`HARVEST/UPDATE/BACKFILL`),null表示查询全局配置
  /// @param at 查询时刻,用于时态过滤,不可为null
  /// @return 包含分页配置的Optional,如果未找到则为空
  Optional<PaginationConfig> findActivePagination(
      Long provenanceId, String operationType, Instant at);

  /// 查询指定时刻有效的HTTP客户端配置。
  ///
  /// @param provenanceId 数据源ID,不可为null
  /// @param operationType 操作类型(`ALL/HARVEST/UPDATE/BACKFILL`),null表示查询全局配置
  /// @param at 查询时刻,用于时态过滤,不可为null
  /// @return 包含HTTP配置的Optional,如果未找到则为空
  Optional<HttpConfig> findActiveHttpConfig(Long provenanceId, String operationType, Instant at);

  /// 查询指定时刻有效的批处理配置。
  ///
  /// @param provenanceId 数据源ID,不可为null
  /// @param operationType 操作类型(`ALL/HARVEST/UPDATE/BACKFILL`),null表示查询全局配置
  /// @param at 查询时刻,用于时态过滤,不可为null
  /// @return 包含批处理配置的Optional,如果未找到则为空
  Optional<BatchingConfig> findActiveBatching(Long provenanceId, String operationType, Instant at);

  /// 查询指定时刻有效的重试策略配置。
  ///
  /// @param provenanceId 数据源ID,不可为null
  /// @param operationType 操作类型(`HARVEST/UPDATE/BACKFILL`),null表示查询全局配置
  /// @param at 查询时刻,用于时态过滤,不可为null
  /// @return 包含重试配置的Optional,如果未找到则为空
  Optional<RetryConfig> findActiveRetry(Long provenanceId, String operationType, Instant at);

  /// 查询指定时刻有效的速率限制配置。
  ///
  /// @param provenanceId 数据源ID,不可为null
  /// @param operationType 操作类型(`HARVEST/UPDATE/BACKFILL`),null表示查询全局配置
  /// @param at 查询时刻,用于时态过滤,不可为null
  /// @return 包含速率限制配置的Optional,如果未找到则为空
  Optional<RateLimitConfig> findActiveRateLimit(
      Long provenanceId, String operationType, Instant at);

  /// 加载完整的数据源配置聚合根。
  ///
  /// 此方法通过查询所有配置维度(数据源元数据、时间窗口偏移、分页、HTTP、批处理、 重试、速率限制)并组装成一个完整的聚合根对象。仅返回在指定时刻对指定操作类型有效的配置。
  ///
  /// 业务规则:
  ///
  /// - 数据源元数据是聚合的必需核心,不存在则返回空
  ///   - 各维度配置为可选,缺失时聚合根中对应字段为null
  ///   - 应用配置作用域优先级规则,高优先级配置覆盖低优先级配置
  ///
  /// @param provenanceId 数据源ID,不可为null
  /// @param operationType 操作类型,null表示查询全局配置
  /// @param at 查询时刻,用于时态过滤,不可为null
  /// @return 包含完整配置聚合根的Optional,如果数据源不存在则为空
  Optional<ProvenanceConfiguration> loadConfiguration(
      Long provenanceId, String operationType, Instant at);
}
