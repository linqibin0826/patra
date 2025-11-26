package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.port.VenueRepository;
import com.patra.catalog.infra.persistence.converter.VenueConverter;
import com.patra.catalog.infra.persistence.mapper.VenueInstanceMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 出版载体聚合根仓储实现。
///
/// **职责**：
///
/// - 管理 Venue（载体）和 VenueInstance（卷期）的持久化
///   - 处理 ISSN/ISBN 唯一性检查
///   - 支持卷期查询和管理
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class VenueRepositoryAdapter implements VenueRepository {

  private final VenueMapper venueMapper;
  private final VenueInstanceMapper venueInstanceMapper;
  private final VenueConverter venueConverter;

  // 方法待添加
}
