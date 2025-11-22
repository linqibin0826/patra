package com.patra.starter.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.lang.Nullable;

/// 处理数据库实体的审计字段自动填充。
///
/// 此处理程序与 MyBatis-Plus 集成，在插入和更新操作期间自动填充时间戳和用户相关的 审计字段。它通过接受可选的 {@link Clock} 实例来支持时间敏感型测试。
@Slf4j
public class AuditMetaObjectHandler implements MetaObjectHandler {

  private final Clock clock;

  /// 创建一个带有可选时钟的审计元数据处理程序。
  ///
  /// @param clock 可选的时钟用于时间敏感型测试，null 使用系统默认值
  public AuditMetaObjectHandler(@Nullable Clock clock) {
    this.clock = clock;
    log.debug("初始化 AuditMetaObjectHandler，时钟: {}", clock);
  }

  /// 在插入操作期间填充审计字段。
  ///
  /// 由于这是初始保存，因此填充创建和更新时间戳。
  ///
  /// @param metaObject 代表要插入的实体的元数据对象
  @Override
  public void insertFill(MetaObject metaObject) {
    Instant now = getCurrentTime();
    fillCreationFields(metaObject, now);
    fillUpdateFields(metaObject, now);
  }

  /// 在更新操作期间填充审计字段。
  ///
  /// 仅填充与更新相关的字段。
  ///
  /// @param metaObject 代表要更新的实体的元数据对象
  @Override
  public void updateFill(MetaObject metaObject) {
    Instant now = getCurrentTime();
    fillUpdateFields(metaObject, now);
  }

  /// 从配置的时钟或系统默认值获取当前时间。
  ///
  /// @return 当前时刻
  private Instant getCurrentTime() {
    return Objects.isNull(clock) ? Instant.now() : Instant.now(clock);
  }

  /// 填充创建相关的审计字段。
  ///
  /// @param metaObject 实体元数据
  /// @param now 要使用的时间戳
  private void fillCreationFields(MetaObject metaObject, Instant now) {
    this.strictInsertFill(metaObject, "createdAt", () -> now, Instant.class);
    // TODO: 从安全上下文填充 createdBy 和 createdByName
  }

  /// 填充更新相关的审计字段。
  ///
  /// @param metaObject 实体元数据
  /// @param now 要使用的时间戳
  private void fillUpdateFields(MetaObject metaObject, Instant now) {
    this.strictUpdateFill(metaObject, "updatedAt", () -> now, Instant.class);
    // TODO: 从安全上下文填充 updatedBy 和 updatedByName
  }
}
