package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// Outbox 消息转换器：应用层值对象 ↔ 持久化数据对象。
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OutboxMessageConverter {

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
  OutboxMessageDO toEntity(OutboxMessage message);

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
  OutboxMessage toDomain(OutboxMessageDO entity);
}
