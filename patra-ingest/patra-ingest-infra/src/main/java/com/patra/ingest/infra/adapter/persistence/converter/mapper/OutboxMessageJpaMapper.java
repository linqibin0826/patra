package com.patra.ingest.infra.adapter.persistence.converter.mapper;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.infra.adapter.persistence.entity.OutboxMessageEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// 发件箱消息 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OutboxMessageJpaMapper {

  @Mapping(
      target = "payloadJson",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(message.getPayloadJson()))")
  @Mapping(
      target = "headersJson",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(message.getHeadersJson()))")
  @Mapping(target = "pubLeaseOwner", source = "leaseOwner")
  @Mapping(target = "pubLeasedUntil", source = "leaseExpireAt")
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
  OutboxMessageEntity toEntity(OutboxMessage message);

  @Mapping(
      target = "payloadJson",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonNodeToString(entity.getPayloadJson()))")
  @Mapping(
      target = "headersJson",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonNodeToString(entity.getHeadersJson()))")
  @Mapping(target = "leaseOwner", source = "pubLeaseOwner")
  @Mapping(target = "leaseExpireAt", source = "pubLeasedUntil")
  OutboxMessage toAggregate(OutboxMessageEntity entity);
}
