package dev.patra.interaction.infra.subject.source;

import dev.patra.catalog.api.venue.dto.VenueResolutionResp;
import dev.patra.catalog.api.venue.dto.VenueResolveByIdsReq;
import dev.patra.catalog.api.venue.dto.VenueResolveByNlmIdsReq;
import dev.patra.catalog.api.venue.dto.VenueResolveResp;
import dev.patra.catalog.api.venue.dto.VenueStateResp;
import dev.patra.catalog.api.venue.endpoint.VenueEndpoint;
import dev.patra.interaction.domain.subject.model.enums.NaturalKeyScheme;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.source.SourceRecord;
import dev.patra.interaction.domain.subject.model.source.SourceState;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
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

/// 期刊的来源策略：经 catalog 的内部 HTTP Interface（`patra-catalog-api` 的 `VenueEndpoint`）解析。
///
/// 锚的取法和文献不同：首次登记（按期刊 ID）取返回的主 NLM；对账（按 NLM）**按请求的 key 对回**，不取返回的
/// `nlmId`。一个期刊可以有主 NLM 和副 NLM，请求副 NLM 时 `nlmId` 返回主 NLM、与 key 不同，锚要保持登记时的值。
/// 快照存期刊全称与 MEDLINE 缩写。失败翻译见 {@link CatalogSourceCalls}。
@Slf4j
@Component
@RequiredArgsConstructor
public class JournalSubjectSourceAdapter implements SubjectSourcePort {

  /// catalog 批量解析接口一次的上限。
  private static final int MAX_BATCH = 100;

  private final VenueEndpoint venues;

  /// 负责期刊。
  ///
  /// @return `JOURNAL`
  @Override
  public SubjectType type() {
    return SubjectType.JOURNAL;
  }

  /// 按 catalog 期刊 ID 解析一个期刊，锚取返回的主 NLM。
  ///
  /// @param sourceId catalog 期刊 ID
  /// @return 来源记录；不存在或没有 NLM 时为空
  @Override
  public Optional<SourceRecord> resolveByKey(long sourceId) {
    VenueResolveResp resp =
        CatalogSourceCalls.call(
            SubjectType.JOURNAL,
            () -> venues.resolveByIds(VenueResolveByIdsReq.of(List.of(sourceId))),
            1);
    return resp.items().stream().findFirst().flatMap(item -> toRecord(item, item.nlmId()));
  }

  /// 按 NLM 批量解析，按响应的 key 对回身份锚（不按顺序，也不按返回的主 NLM）。
  ///
  /// @param keys 身份锚，1 ~ 100 个
  /// @return 存在的期刊
  @Override
  public Map<NaturalKey, SourceRecord> resolveByNaturalKeys(Collection<NaturalKey> keys) {
    if (keys.isEmpty() || keys.size() > MAX_BATCH) {
      throw new IllegalArgumentException("一次解析 1 ~ " + MAX_BATCH + " 个身份锚：" + keys.size());
    }
    List<String> nlmIds = keys.stream().map(NaturalKey::value).toList();
    VenueResolveResp resp =
        CatalogSourceCalls.call(
            SubjectType.JOURNAL,
            () -> venues.resolveByNlmIds(VenueResolveByNlmIdsReq.of(nlmIds)),
            nlmIds.size());
    return resp.items().stream()
        .map(item -> toRecord(item, item.key()))
        .flatMap(Optional::stream)
        .collect(Collectors.toUnmodifiableMap(SourceRecord::naturalKey, r -> r));
  }

  /// 一行解析结果 → 来源记录。不存在、锚为空或不合 NLM 的格式（没有锚就不能登记）时为空。
  ///
  /// @param item 解析结果
  /// @param anchor 这一行的锚：首次登记是主 NLM，对账是请求的 key
  /// @return 来源记录
  private static Optional<SourceRecord> toRecord(VenueResolutionResp item, String anchor) {
    if (!item.exists()) {
      return Optional.empty();
    }
    if (!NaturalKeyScheme.NLM.accepts(anchor)) {
      log.warn("catalog 期刊没有合法的 NLM ID，不能登记: id={}, nlmId={}", item.id(), anchor);
      return Optional.empty();
    }
    return Optional.of(
        SourceRecord.of(
            item.id(),
            NaturalKey.of(NaturalKeyScheme.NLM, anchor),
            item.state() == VenueStateResp.DELETED ? SourceState.DELETED : SourceState.ACTIVE,
            SubjectSnapshot.of(item.title(), null, null, item.abbreviatedTitle())));
  }
}
