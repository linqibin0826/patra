package com.patra.catalog.infra.persistence.repository;

import com.patra.catalog.domain.port.VenuePort;
import com.patra.catalog.infra.persistence.converter.VenueConverter;
import com.patra.catalog.infra.persistence.mapper.VenueInstanceMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 出版载体聚合根仓储实现。
 *
 * <p><b>职责</b>：
 *
 * <ul>
 *   <li>管理 Venue（载体）和 VenueInstance（卷期）的持久化
 *   <li>处理 ISSN/ISBN 唯一性检查
 *   <li>支持卷期查询和管理
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class VenueRepositoryImpl implements VenuePort {

  private final VenueMapper venueMapper;
  private final VenueInstanceMapper venueInstanceMapper;
  private final VenueConverter venueConverter;

  // 方法待添加
}
