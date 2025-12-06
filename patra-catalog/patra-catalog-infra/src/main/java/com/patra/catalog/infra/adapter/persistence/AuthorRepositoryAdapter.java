package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.port.repository.AuthorRepository;
import com.patra.catalog.infra.persistence.converter.AuthorConverter;
import com.patra.catalog.infra.persistence.mapper.AuthorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 作者聚合根仓储实现。
///
/// **职责**：
///
/// - 管理作者聚合根的持久化
///   - 处理 ORCID 唯一性检查
///   - 支持去重查询和相似度匹配
///   - 提供按标识符(ORCID/ResearcherID/ScopusID)查询
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class AuthorRepositoryAdapter implements AuthorRepository {

  private final AuthorMapper authorMapper;
  private final AuthorConverter authorConverter;

  // 方法待添加
}
