package com.patra.ingest.infra.adapter.persistence.converter.mapper;

import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.infra.adapter.persistence.entity.OutboxRelayLogEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// 发件箱中继日志 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// **设计说明**：
///
/// - **仅追加**：无更新方法（日志创建后不可变）
/// - **批量优化**：批量转换方法用于高吞吐量中继作业
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OutboxRelayLogJpaMapper {

  /// 将领域实体转换为 JPA 实体（用于插入操作）。
  @Mapping(target = "messageId", source = "outboxMessageId")
  @Mapping(target = "relayStatus", expression = "java(log.getRelayStatus().getCode())")
  // 审计字段由 JPA 管理
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  OutboxRelayLogEntity toEntity(OutboxRelayLog log);

  /// 将 JPA 实体转换为领域实体（用于查询结果映射）。
  @Mapping(target = "outboxMessageId", source = "messageId")
  @Mapping(
      target = "relayStatus",
      expression =
          "java(com.patra.ingest.domain.model.enums.RelayStatus.fromCode(entity.getRelayStatus()))")
  OutboxRelayLog toAggregate(OutboxRelayLogEntity entity);

  /// 批量将领域实体转换为 JPA 实体（用于批量插入）。
  List<OutboxRelayLogEntity> toEntities(List<OutboxRelayLog> logs);

  /// 批量将 JPA 实体转换为领域实体（用于查询结果）。
  List<OutboxRelayLog> toAggregates(List<OutboxRelayLogEntity> entities);
}
