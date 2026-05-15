package dev.linqibin.patra.ingest.infra.adapter.persistence;

import dev.linqibin.patra.ingest.domain.model.entity.CursorEvent;
import dev.linqibin.patra.ingest.domain.port.CursorEventRepository;
import dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper.CursorEventJpaMapper;
import dev.linqibin.patra.ingest.infra.adapter.persistence.dao.CursorEventDao;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.CursorEventEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 游标推进事件（CursorEvent）仓储实现，基于 JPA。
///
/// 职责：记录游标 prev → new 的推进轨迹（含窗口与水位），支撑审计 / 回放。
///
/// 仅提供保存；不提供复杂检索（需统计请建立独立 Query 组件）。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
@Slf4j
public class CursorEventRepositoryAdapter implements CursorEventRepository {

  /// CursorEvent JPA Repository
  private final CursorEventDao cursorEventDao;

  /// 领域实体与 JPA 实体转换器
  private final CursorEventJpaMapper cursorEventJpaMapper;

  /// 保存游标事件。
  ///
  /// @param event 游标事件实体
  /// @return 持久化后实体
  @Override
  public CursorEvent save(CursorEvent event) {
    CursorEventEntity entity = cursorEventJpaMapper.toEntity(event);

    if (event.getId() == null) {
      // 新增：预分配雪花 ID
      entity.setId(SnowflakeIdGenerator.getId());
      if (log.isDebugEnabled()) {
        log.debug(
            "cursor event insert cursorKey={} wm={}",
            entity.getCursorKey(),
            entity.getNewInstant());
      }
    } else {
      // 更新：使用现有 ID（游标事件通常不更新，但保留此逻辑以防万一）
      entity.setId(event.getId());
      if (log.isDebugEnabled()) {
        log.debug(
            "cursor event update id={} cursorKey={} wm={}",
            entity.getId(),
            entity.getCursorKey(),
            entity.getNewInstant());
      }
    }

    CursorEventEntity saved = cursorEventDao.save(entity);
    return cursorEventJpaMapper.toAggregate(saved);
  }
}
