package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.port.PublicationRepository;
import com.patra.catalog.infra.persistence.converter.PublicationConverter;
import com.patra.catalog.infra.persistence.mapper.PublicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 文献聚合根仓储实现（基础设施层）。
///
/// **职责**：
///
/// - 实现 PublicationRepository 接口定义的仓储契约
///   - 负责 Aggregate ↔ DO 转换
///   - 协调 MyBatis Mapper 操作
///   - 处理跨表数据组装（Publication + Metadata + Abstract）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class PublicationRepositoryAdapter implements PublicationRepository {

  private final PublicationMapper publicationMapper;
  private final PublicationConverter publicationConverter;

  // 方法待添加
}
