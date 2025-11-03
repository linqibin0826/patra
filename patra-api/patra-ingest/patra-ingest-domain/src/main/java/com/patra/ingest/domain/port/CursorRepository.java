package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.Cursor;
import java.time.Instant;
import java.util.Optional;

/**
 * 采集游标(Cursor)仓储端口(六边形架构 - Domain → Infrastructure)。
 *
 * <p><b>职责</b>: 持久化和查询采集/清洗流程的 Cursor 水位线(watermark),确保:
 *
 * <ul>
 *   <li>去重保证 - 避免重复采集相同数据窗口
 *   <li>窗口连续性 - 防止窗口漂移或遗漏
 *   <li>增量采集 - 支持从上次中断位置继续
 * </ul>
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>仓储端口(Repository Port)</b>,定义在 Domain
 * 层,由基础设施层(Infrastructure)实现,确保领域逻辑与持久化技术解耦。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CursorRepository {

  /**
   * 根据业务主键查询 Cursor。
   *
   * <p><b>业务含义</b>: 通过五元组(provenance + operation + cursorKey + namespace)定位唯一 Cursor。
   *
   * @param provenanceCode Provenance 代码(数据源标识)
   * @param operationCode 操作代码(可为 null,作为过滤条件)
   * @param cursorKey Cursor 标识符(例如数据域或资源类型)
   * @param namespaceScope 命名空间作用域代码
   * @param namespaceKey 命名空间业务主键
   * @return 匹配的 Cursor 实体,或 {@link Optional#empty()}
   */
  Optional<Cursor> find(
      String provenanceCode,
      String operationCode,
      String cursorKey,
      String namespaceScope,
      String namespaceKey);

  /**
   * 持久化或更新 Cursor 状态。
   *
   * <p><b>业务含义</b>: 保存 Cursor 的当前水位线、命名空间、版本信息等。
   *
   * @param cursor Cursor 实体,包含当前水位线、命名空间、版本信息
   * @return 已持久化的 Cursor 实体
   */
  Cursor save(Cursor cursor);

  /**
   * 获取 GLOBAL 命名空间的最新归一化时间水位线。
   *
   * <p><b>业务含义</b>: 查询全局时间 Cursor 的最新水位线,用于跨数据源的时间对齐。
   *
   * @param provenanceCode Provenance 代码
   * @param operationCode 操作代码过滤条件(可为 null)
   * @return 最新的 GLOBAL 时间水位线,或 {@link Optional#empty()}
   */
  Optional<Instant> findLatestGlobalTimeWatermark(String provenanceCode, String operationCode);
}
