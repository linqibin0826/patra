package dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper;

import dev.linqibin.patra.ingest.domain.model.entity.OutboxMessage;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.OutboxMessageEntity;
import dev.linqibin.starter.jpa.json.JsonColumnCodec;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/// 发件箱消息 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class OutboxMessageJpaMapper {

  /// JSON 列转换器，由容器注入。
  @Autowired protected JsonColumnCodec codec;

  @Mapping(target = "payloadJson", expression = "java(codec.read(message.getPayloadJson()))")
  @Mapping(target = "headersJson", expression = "java(codec.read(message.getHeadersJson()))")
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
  public abstract OutboxMessageEntity toEntity(OutboxMessage message);

  @Mapping(target = "payloadJson", expression = "java(codec.write(entity.getPayloadJson()))")
  @Mapping(target = "headersJson", expression = "java(codec.write(entity.getHeadersJson()))")
  @Mapping(target = "leaseOwner", source = "pubLeaseOwner")
  @Mapping(target = "leaseExpireAt", source = "pubLeasedUntil")
  public abstract OutboxMessage toAggregate(OutboxMessageEntity entity);
}
