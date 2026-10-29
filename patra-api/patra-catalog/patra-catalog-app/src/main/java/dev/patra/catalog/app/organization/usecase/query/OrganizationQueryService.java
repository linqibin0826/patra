package dev.patra.catalog.app.organization.usecase.query;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.organization.exception.OrganizationNotFoundException;
import dev.patra.catalog.domain.organization.model.read.OrganizationSuggestion;
import dev.patra.catalog.domain.organization.model.read.OrganizationSummary;
import dev.patra.catalog.domain.organization.port.read.OrganizationReadPort;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 机构 CQRS 查询服务：门户的建议与 identity 的按 ROR ID 查询。
///
/// 委托 [OrganizationReadPort]；ROR ID 格式不对时直接当不存在，不查库。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class OrganizationQueryService {

  /// 9 位裸 ROR ID：`0` 开头，小写字母与数字。
  private static final Pattern ROR_ID = Pattern.compile("^0[a-z0-9]{8}$");

  private final OrganizationReadPort readPort;

  /// 边打边搜的机构建议。
  ///
  /// @param query 已归一化的查询
  /// @return 候选列表，没有命中为空列表
  @Transactional(readOnly = true)
  public List<OrganizationSuggestion> suggest(SuggestQuery query) {
    return readPort.suggest(query);
  }

  /// 按 9 位裸 ROR ID 取机构摘要。
  ///
  /// @param rorId 裸 ROR ID
  /// @return 机构摘要
  /// @throws OrganizationNotFoundException 格式不对、不存在或非 active
  @Transactional(readOnly = true)
  public OrganizationSummary getByRorId(String rorId) {
    if (rorId == null || !ROR_ID.matcher(rorId).matches()) {
      throw new OrganizationNotFoundException(rorId);
    }
    return readPort.findByRorId(rorId).orElseThrow(() -> new OrganizationNotFoundException(rorId));
  }
}
