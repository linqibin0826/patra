package com.patra.starter.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.lang.Nullable;

/**
 * Handles automatic population of audit fields for database entities.
 *
 * <p>This handler integrates with MyBatis-Plus to automatically fill timestamp and user-related
 * audit fields during insert and update operations. It supports time-sensitive testing by accepting
 * an optional {@link Clock} instance.
 */
@Slf4j
public class AuditMetaObjectHandler implements MetaObjectHandler {

  private final Clock clock;

  /**
   * Creates an audit metadata handler with an optional clock.
   *
   * @param clock optional clock for time-sensitive testing, null uses system default
   */
  public AuditMetaObjectHandler(@Nullable Clock clock) {
    this.clock = clock;
    log.debug("Initialized AuditMetaObjectHandler with clock: {}", clock);
  }

  /**
   * Fills audit fields during insert operations.
   *
   * <p>Populates both creation and update timestamps since this is the initial save.
   *
   * @param metaObject the metadata object representing the entity being inserted
   */
  @Override
  public void insertFill(MetaObject metaObject) {
    Instant now = getCurrentTime();
    fillCreationFields(metaObject, now);
    fillUpdateFields(metaObject, now);
  }

  /**
   * Fills audit fields during update operations.
   *
   * <p>Only populates update-related fields.
   *
   * @param metaObject the metadata object representing the entity being updated
   */
  @Override
  public void updateFill(MetaObject metaObject) {
    Instant now = getCurrentTime();
    fillUpdateFields(metaObject, now);
  }

  /**
   * Obtains the current time from the configured clock or system default.
   *
   * @return the current instant
   */
  private Instant getCurrentTime() {
    return Objects.isNull(clock) ? Instant.now() : Instant.now(clock);
  }

  /**
   * Populates creation-related audit fields.
   *
   * @param metaObject the entity metadata
   * @param now the timestamp to use
   */
  private void fillCreationFields(MetaObject metaObject, Instant now) {
    this.strictInsertFill(metaObject, "createdAt", () -> now, Instant.class);
    // TODO: Populate createdBy and createdByName from security context
  }

  /**
   * Populates update-related audit fields.
   *
   * @param metaObject the entity metadata
   * @param now the timestamp to use
   */
  private void fillUpdateFields(MetaObject metaObject, Instant now) {
    this.strictUpdateFill(metaObject, "updatedAt", () -> now, Instant.class);
    // TODO: Populate updatedBy and updatedByName from security context
  }
}
