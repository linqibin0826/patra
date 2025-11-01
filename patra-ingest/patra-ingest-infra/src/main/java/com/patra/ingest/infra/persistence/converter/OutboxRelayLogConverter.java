package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.infra.persistence.entity.OutboxRelayLogDO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Outbox relay log converter: Domain entity ↔ Persistence DO.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Convert domain entity {@link OutboxRelayLog} to persistence DO {@link OutboxRelayLogDO}
 *   <li>Convert persistence DO back to domain entity
 *   <li>Support batch conversions for efficient repository operations
 * </ul>
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Uses MapStruct for type-safe, compile-time code generation
 *   <li>Registered as Spring bean (componentModel = "spring")
 *   <li>Field mappings mostly 1:1 (messageId ↔ messageId, relayBatchId ↔ relayBatchId)
 *   <li>No custom transformations needed (no JSON fields, no enum conversions)
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OutboxRelayLogConverter {

  /**
   * Converts domain entity to persistence DO (for insert operations).
   *
   * <p>Field mappings:
   *
   * <ul>
   *   <li>outboxMessageId → messageId
   *   <li>relayStatus (RelayStatus enum) → relayStatus (String via .getCode())
   *   <li>All other fields map 1:1 by name
   * </ul>
   *
   * @param log domain entity
   * @return persistence DO
   */
  @Mapping(target = "messageId", source = "outboxMessageId")
  @Mapping(target = "relayStatus", expression = "java(log.getRelayStatus().getCode())")
  OutboxRelayLogDO toEntity(OutboxRelayLog log);

  /**
   * Converts persistence DO to domain entity (for query result mapping).
   *
   * <p>Field mappings:
   *
   * <ul>
   *   <li>messageId → outboxMessageId
   *   <li>relayStatus (String) → relayStatus (RelayStatus enum via fromCode())
   *   <li>All other fields map 1:1 by name
   * </ul>
   *
   * @param entity persistence DO
   * @return domain entity
   */
  @Mapping(target = "outboxMessageId", source = "messageId")
  @Mapping(
      target = "relayStatus",
      expression =
          "java(com.patra.ingest.domain.model.enums.RelayStatus.fromCode(entity.getRelayStatus()))")
  OutboxRelayLog toDomain(OutboxRelayLogDO entity);

  /**
   * Batch converts domain entities to persistence DOs (for batch insert).
   *
   * <p>Use case: Relay job completes 100-500 relays, convert all logs at once.
   *
   * @param logs list of domain entities
   * @return list of persistence DOs
   */
  List<OutboxRelayLogDO> toEntities(List<OutboxRelayLog> logs);

  /**
   * Batch converts persistence DOs to domain entities (for query results).
   *
   * <p>Use case: Query returns multiple relay logs, convert all to domain entities.
   *
   * @param entities list of persistence DOs
   * @return list of domain entities
   */
  List<OutboxRelayLog> toDomains(List<OutboxRelayLogDO> entities);
}
