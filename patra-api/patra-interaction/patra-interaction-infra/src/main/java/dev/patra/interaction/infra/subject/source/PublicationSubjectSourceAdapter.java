package dev.patra.interaction.infra.subject.source;

import dev.patra.catalog.api.publication.dto.PublicationResolutionResp;
import dev.patra.catalog.api.publication.dto.PublicationResolveByIdsReq;
import dev.patra.catalog.api.publication.dto.PublicationResolveByPmidsReq;
import dev.patra.catalog.api.publication.dto.PublicationResolveResp;
import dev.patra.catalog.api.publication.dto.PublicationStateResp;
import dev.patra.catalog.api.publication.endpoint.PublicationEndpoint;
import dev.patra.interaction.domain.subject.model.enums.NaturalKeyScheme;
import dev.patra.interaction.domain.subject.model.enums.SourceState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SourceRecord;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.source.SubjectSourcePort;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 文献的来源策略：经 catalog 的内部 HTTP Interface（`patra-catalog-api` 的 `PublicationEndpoint`）解析。
///
/// 失败翻译见 {@link CatalogSourceCalls}：任何失败都是 `SubjectSourceUnavailableException`。
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicationSubjectSourceAdapter implements SubjectSourcePort {

  /// catalog 批量解析接口一次的上限。
  private static final int MAX_BATCH = 100;

  private final PublicationEndpoint publications;

  /// 负责文献。
  ///
  /// @return `PUBLICATION`
  @Override
  public SubjectType type() {
    return SubjectType.PUBLICATION;
  }

  /// 按 catalog 内部 ID 解析一篇。
  ///
  /// @param sourceId catalog 内部 ID
  /// @return 来源记录；不存在或没有 PMID 时为空
  @Override
  public Optional<SourceRecord> resolveByKey(long sourceId) {
    PublicationResolveResp resp =
        CatalogSourceCalls.call(
            SubjectType.PUBLICATION,
            () -> publications.resolveByIds(PublicationResolveByIdsReq.of(List.of(sourceId))),
            1);
    return resp.items().stream().findFirst().flatMap(PublicationSubjectSourceAdapter::toRecord);
  }

  /// 按 PMID 批量解析，按响应里的 PMID 对回身份锚（不按顺序）。
  ///
  /// @param keys 身份锚，1 ~ 100 个
  /// @return 存在的文献
  @Override
  public Map<NaturalKey, SourceRecord> resolveByNaturalKeys(Collection<NaturalKey> keys) {
    if (keys.isEmpty() || keys.size() > MAX_BATCH) {
      throw new IllegalArgumentException("一次解析 1 ~ " + MAX_BATCH + " 个身份锚：" + keys.size());
    }
    List<String> pmids = keys.stream().map(NaturalKey::value).toList();
    PublicationResolveResp resp =
        CatalogSourceCalls.call(
            SubjectType.PUBLICATION,
            () -> publications.resolveByPmids(PublicationResolveByPmidsReq.of(pmids)),
            pmids.size());
    return resp.items().stream()
        .map(PublicationSubjectSourceAdapter::toRecord)
        .flatMap(Optional::stream)
        .collect(Collectors.toUnmodifiableMap(SourceRecord::naturalKey, r -> r));
  }

  /// 一行解析结果 → 来源记录。不存在、没有 PMID 或 PMID 不合格式（没有锚就不能登记）时为空。
  ///
  /// @param item 解析结果
  /// @return 来源记录
  private static Optional<SourceRecord> toRecord(PublicationResolutionResp item) {
    if (!item.exists()) {
      return Optional.empty();
    }
    if (!NaturalKeyScheme.PMID.accepts(item.pmid())) {
      log.warn("catalog 文献没有合法的 PMID，不能登记: id={}, pmid={}", item.id(), item.pmid());
      return Optional.empty();
    }
    return Optional.of(
        SourceRecord.of(
            item.id(),
            NaturalKey.of(NaturalKeyScheme.PMID, item.pmid()),
            item.state() == PublicationStateResp.DELETED ? SourceState.DELETED : SourceState.ACTIVE,
            SubjectSnapshot.of(item.title(), item.journal(), publishedDate(item), null)));
  }

  /// 出版日期：不合降精度写法时按「无日期」处理并记 WARN，不让一行坏数据拖垮整页对账或一次收藏。
  ///
  /// @param item 解析结果
  /// @return 出版日期，可空
  private static String publishedDate(PublicationResolutionResp item) {
    String date = item.publishedDate();
    if (date != null && !SubjectSnapshot.isValidPublishedDate(date)) {
      log.warn("catalog 出版日期格式不对，按无日期处理: pmid={}, publishedDate={}", item.pmid(), date);
      return null;
    }
    return date;
  }
}
