package dev.linqibin.patra.catalog.app.usecase.portal.query;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.app.usecase.portal.query.dto.PortalFeedQuery;
import dev.linqibin.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFilter;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchSort;
import dev.linqibin.patra.catalog.domain.port.read.PublicationSearchReadPort;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// Portal 首页文献流 CQRS 查询服务。
///
/// feed 是"无筛选、按 tab 排序"的文献检索特例：归一化分页、把 tab 映射为
/// [PublicationSearchSort] 后委托 [PublicationSearchReadPort]。只读，无 `@Transactional`。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class PortalFeedQueryService {

  /// Portal 文献流默认每页大小（首页一屏视觉密度）。
  private static final int DEFAULT_PAGE_SIZE = 14;

  /// Portal 文献流每页大小上限。
  private static final int MAX_PAGE_SIZE = 50;

  private final PublicationSearchReadPort readPort;

  /// 查询 portal 文献流。
  ///
  /// @param query 未归一化的外部查询参数
  /// @return 分页结果
  /// @throws IllegalArgumentException 当 tab 非法时
  public PageResult<PortalPaperReadModel> listFeed(PortalFeedQuery query) {
    Objects.requireNonNull(query, "query must not be null");
    PagingParams paging =
        PagingParams.normalize(query.page(), query.pageSize(), DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
    PublicationSearchFilter filter =
        PublicationSearchFilter.builder().sort(toSort(query.tab())).build();
    return readPort.search(filter, paging);
  }

  /// feed tab → 排序：recent（默认）→ LATEST，cited → CITED，大小写不敏感。
  ///
  /// @param tab tab 字符串，可为 null
  /// @return 排序枚举
  /// @throws IllegalArgumentException 当 tab 不是 recent / cited 时
  private static PublicationSearchSort toSort(String tab) {
    if (tab == null) {
      return PublicationSearchSort.LATEST;
    }
    return switch (tab.toLowerCase(Locale.ROOT)) {
      case "recent" -> PublicationSearchSort.LATEST;
      case "cited" -> PublicationSearchSort.CITED;
      default -> throw new IllegalArgumentException("Unsupported feed tab: " + tab);
    };
  }
}
