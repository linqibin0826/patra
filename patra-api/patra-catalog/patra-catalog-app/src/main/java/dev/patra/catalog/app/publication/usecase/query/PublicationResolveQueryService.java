package dev.patra.catalog.app.publication.usecase.query;

import dev.patra.catalog.domain.publication.model.read.PublicationResolveReadModel;
import dev.patra.catalog.domain.publication.port.read.PublicationResolveReadPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 文献批量解析 CQRS 查询服务（给 interaction 的对象登记表）。
///
/// 每个请求键输出一行：按请求顺序、重复键只留第一次，查不到的键 `found` 为 null；软删的文献照样返回。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class PublicationResolveQueryService {

  private final PublicationResolveReadPort readPort;

  /// 按内部 ID 批量解析。
  ///
  /// @param ids 文献主键（调用方已校验 1 ~ 100 个正整数）
  /// @return 每个去重后的键一行，按请求顺序
  @Transactional(readOnly = true)
  public List<PublicationResolution> resolveByIds(List<Long> ids) {
    List<Long> keys = List.copyOf(new LinkedHashSet<>(ids));
    Map<Long, PublicationResolveReadModel> found =
        readPort.findByIds(keys).stream()
            .collect(Collectors.toMap(PublicationResolveReadModel::id, Function.identity()));
    return keys.stream()
        .map(id -> PublicationResolution.of(Long.toString(id), found.get(id)))
        .toList();
  }

  /// 按 PMID 批量解析。
  ///
  /// @param pmids PubMed ID（调用方已校验格式与 1 ~ 100 个）
  /// @return 每个去重后的键一行，按请求顺序
  @Transactional(readOnly = true)
  public List<PublicationResolution> resolveByPmids(List<String> pmids) {
    List<String> keys = List.copyOf(new LinkedHashSet<>(pmids));
    Map<String, PublicationResolveReadModel> found =
        readPort.findByPmids(keys).stream()
            .collect(Collectors.toMap(PublicationResolveReadModel::pmid, Function.identity()));
    return keys.stream().map(pmid -> PublicationResolution.of(pmid, found.get(pmid))).toList();
  }
}
