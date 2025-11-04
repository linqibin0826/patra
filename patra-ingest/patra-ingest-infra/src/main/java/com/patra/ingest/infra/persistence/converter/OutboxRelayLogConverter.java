package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.infra.persistence.entity.OutboxRelayLogDO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 发件箱中继日志对象转换器,负责领域对象与数据库实体转换。
 *
 * <p>职责:
 *
 * <ul>
 *   <li>将领域实体 {@link OutboxRelayLog} 转换为持久化 DO {@link OutboxRelayLogDO}
 *   <li>将持久化 DO 转换回领域实体
 *   <li>支持批量转换以提高仓储操作效率
 * </ul>
 *
 * <p>设计说明:
 *
 * <ul>
 *   <li>使用 MapStruct 实现类型安全的编译时代码生成
 *   <li>注册为 Spring Bean(componentModel = "spring")
 *   <li>字段映射大多为 1:1(messageId ↔ messageId、relayBatchId ↔ relayBatchId)
 *   <li>无需自定义转换(无 JSON 字段、无枚举转换)
 * </ul>
 *
 * @author Patra Team
 * @since 2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OutboxRelayLogConverter {

  /**
   * 将领域实体转换为持久化 DO(用于插入操作)。
   *
   * <p>字段映射:
   *
   * <ul>
   *   <li>outboxMessageId → messageId
   *   <li>relayStatus(RelayStatus 枚举) → relayStatus(String,通过 .getCode())
   *   <li>所有其他字段按名称 1:1 映射
   * </ul>
   *
   * @param log 领域实体
   * @return 持久化 DO
   */
  @Mapping(target = "messageId", source = "outboxMessageId")
  @Mapping(target = "relayStatus", expression = "java(log.getRelayStatus().getCode())")
  OutboxRelayLogDO toEntity(OutboxRelayLog log);

  /**
   * 将持久化 DO 转换为领域实体(用于查询结果映射)。
   *
   * <p>字段映射:
   *
   * <ul>
   *   <li>messageId → outboxMessageId
   *   <li>relayStatus(String) → relayStatus(RelayStatus 枚举,通过 fromCode())
   *   <li>所有其他字段按名称 1:1 映射
   * </ul>
   *
   * @param entity 持久化 DO
   * @return 领域实体
   */
  @Mapping(target = "outboxMessageId", source = "messageId")
  @Mapping(
      target = "relayStatus",
      expression =
          "java(com.patra.ingest.domain.model.enums.RelayStatus.fromCode(entity.getRelayStatus()))")
  OutboxRelayLog toDomain(OutboxRelayLogDO entity);

  /**
   * 批量将领域实体转换为持久化 DO(用于批量插入)。
   *
   * <p>使用场景: 中继作业完成 100-500 个中继,一次性转换所有日志。
   *
   * @param logs 领域实体列表
   * @return 持久化 DO 列表
   */
  List<OutboxRelayLogDO> toEntities(List<OutboxRelayLog> logs);

  /**
   * 批量将持久化 DO 转换为领域实体(用于查询结果)。
   *
   * <p>使用场景: 查询返回多个中继日志,全部转换为领域实体。
   *
   * @param entities 持久化 DO 列表
   * @return 领域实体列表
   */
  List<OutboxRelayLog> toDomains(List<OutboxRelayLogDO> entities);
}
