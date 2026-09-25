package dev.linqibin.patra.catalog.infra.adapter.read;

import java.time.Instant;

/// [dev.linqibin.patra.catalog.infra.persistence.dao.PublicationSearchDao#findLastSyncedAt] 的接口投影。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationLastSyncedRow {

  /// 全库 `max(last_synced_at)`；空库为 null。
  Instant getLastSyncedAt();
}
