package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.infra.persistence.entity.OutboxRelayLogDO;
import java.time.Instant;
import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/// 发件箱中继日志对象转换器,负责领域对象与数据库实体转换。
///
/// 职责:
///
/// - 将领域实体 {@link OutboxRelayLog} 转换为持久化 DO {@link OutboxRelayLogDO}
///   - 将持久化 DO 转换回领域实体
///   - 支持批量转换以提高仓储操作效率
///
/// 设计说明:
///
/// - 使用 MapStruct 实现类型安全的编译时代码生成
///   - 注册为 Spring Bean(componentModel = "spring")
///   - 字段映射大多为 1:1(messageId ↔ messageId、relayBatchId ↔ relayBatchId)
///   - 无需自定义转换(无 JSON 字段、无枚举转换)
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OutboxRelayLogConverter {

  /// 将领域实体转换为持久化 DO(用于插入操作)。
  ///
  /// 字段映射:
  ///
  /// - outboxMessageId → messageId
  ///   - relayStatus(RelayStatus 枚举) → relayStatus(String,通过 .getCode())
  ///   - 所有其他字段按名称 1:1 映射
  ///
  /// @param log 领域实体
  /// @return 持久化 DO
  @Mapping(target = "messageId", source = "outboxMessageId")
  @Mapping(target = "relayStatus", expression = "java(log.getRelayStatus().getCode())")
  OutboxRelayLogDO toEntity(OutboxRelayLog log);

  /// 为批量插入初始化审计字段默认值。
  ///
  /// 虽然 `insertBatchSomeColumn` 会触发 `MetaObjectHandler.insertFill()`，
  /// 但在 Converter 阶段预设值可以确保：
  /// 1. 批量操作中所有记录的时间戳一致性（同一批次使用相同时间）
  /// 2. 避免依赖 MetaObjectHandler 的隐式行为
  ///
  /// @param target 目标 DO 对象
  /// @param source 源领域对象
  @AfterMapping
  default void initializeDefaults(@MappingTarget OutboxRelayLogDO target, OutboxRelayLog source) {
    // 仅对新增记录（无 ID）设置默认值
    if (source.getId() == null) {
      Instant now = Instant.now();
      if (target.getCreatedAt() == null) {
        target.setCreatedAt(now);
      }
      if (target.getUpdatedAt() == null) {
        target.setUpdatedAt(now);
      }
      if (target.getVersion() == null) {
        target.setVersion(0L);
      }
    }
  }

  /// 将持久化 DO 转换为领域实体(用于查询结果映射)。
  ///
  /// 字段映射:
  ///
  /// - messageId → outboxMessageId
  ///   - relayStatus(String) → relayStatus(RelayStatus 枚举,通过 fromCode())
  ///   - 所有其他字段按名称 1:1 映射
  ///
  /// @param entity 持久化 DO
  /// @return 领域实体
  @Mapping(target = "outboxMessageId", source = "messageId")
  @Mapping(
      target = "relayStatus",
      expression =
          "java(com.patra.ingest.domain.model.enums.RelayStatus.fromCode(entity.getRelayStatus()))")
  OutboxRelayLog toDomain(OutboxRelayLogDO entity);

  /// 批量将领域实体转换为持久化 DO(用于批量插入)。
  ///
  /// 使用场景: 中继作业完成 100-500 个中继,一次性转换所有日志。
  ///
  /// @param logs 领域实体列表
  /// @return 持久化 DO 列表
  List<OutboxRelayLogDO> toEntities(List<OutboxRelayLog> logs);

  /// 批量将持久化 DO 转换为领域实体(用于查询结果)。
  ///
  /// 使用场景: 查询返回多个中继日志,全部转换为领域实体。
  ///
  /// @param entities 持久化 DO 列表
  /// @return 领域实体列表
  List<OutboxRelayLog> toDomains(List<OutboxRelayLogDO> entities);
}
