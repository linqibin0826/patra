package dev.patra.interaction.infra.subject.source;

import dev.patra.catalog.api.dto.publication.PublicationResolutionResp;
import dev.patra.catalog.api.dto.publication.PublicationResolveByIdsReq;
import dev.patra.catalog.api.dto.publication.PublicationResolveByPmidsReq;
import dev.patra.catalog.api.dto.publication.PublicationResolveResp;
import dev.patra.catalog.api.dto.publication.PublicationStateResp;
import dev.patra.catalog.api.endpoint.PublicationEndpoint;
import dev.patra.interaction.domain.subject.exception.SubjectSourceUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.NaturalKeyScheme;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.source.SourceRecord;
import dev.patra.interaction.domain.subject.model.source.SourceState;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.source.SubjectSourcePort;
import dev.patra.tool.core.error.remote.RemoteCallException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/// 文献的来源策略：经 catalog 的内部 HTTP Interface（`patra-catalog-api` 的 `PublicationEndpoint`）解析。
///
/// 下游的三种失败都翻译成 {@link SubjectSourceUnavailableException}：回了错误响应
/// （`RemoteCallException`；这两个接口不用 404，422 / 400 是本服务的程序错误，日志里看得出来）；连不上或超时
/// （`ResourceAccessException`）；注册中心里没有 catalog 实例（`IllegalStateException`）。`try` 只包住一次代理调用。
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
        call(() -> publications.resolveByIds(PublicationResolveByIdsReq.of(List.of(sourceId))), 1);
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
        call(
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
            SubjectSnapshot.of(item.title(), item.journal(), publishedDate(item))));
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

  /// 调一次内部接口并翻译失败。
  ///
  /// @param request 代理调用
  /// @param keyCount 请求键个数（日志用）
  /// @return 响应
  /// @throws SubjectSourceUnavailableException 任何失败
  private static PublicationResolveResp call(
      Supplier<PublicationResolveResp> request, int keyCount) {
    try {
      return request.get();
    } catch (RemoteCallException ex) {
      log.warn(
          "catalog 解析接口返回错误: keys={}, httpStatus={}, errorCode={}",
          keyCount,
          ex.getHttpStatus(),
          ex.getErrorCode());
      throw new SubjectSourceUnavailableException(ex);
    } catch (ResourceAccessException | IllegalStateException ex) {
      log.warn("catalog 解析接口不可用: keys={}, cause={}", keyCount, ex.getClass().getSimpleName());
      throw new SubjectSourceUnavailableException(ex);
    }
  }
}
