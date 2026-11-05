package dev.patra.analytics.infra.insight.catalog;

import dev.patra.analytics.domain.insight.exception.InsightUnavailableException;
import dev.patra.analytics.domain.insight.model.enums.AlsoReadSource;
import dev.patra.analytics.domain.insight.model.vo.AlsoReadItem;
import dev.patra.analytics.domain.insight.port.catalog.PublicationCatalogPort;
import dev.patra.catalog.api.publication.dto.PublicationResolutionResp;
import dev.patra.catalog.api.publication.dto.PublicationResolveByIdsReq;
import dev.patra.catalog.api.publication.dto.PublicationStateResp;
import dev.patra.catalog.api.publication.dto.SubjectNeighborResp;
import dev.patra.catalog.api.publication.endpoint.PublicationEndpoint;
import dev.patra.catalog.api.publication.endpoint.PublicationNeighborEndpoint;
import dev.patra.tool.core.error.remote.RemoteCallException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.ResourceAccessException;

/// `PublicationCatalogPort` 的 HTTP Interface 实现：调 catalog 的两个内部契约（insight spec §8）。
///
/// 远程错误、连不上、找不到实例都算 catalog 不可用：任务失败、旧快照保留；看过还看了只少补位。
@Slf4j
public class PublicationCatalogAdapter implements PublicationCatalogPort {

  /// `resolve/by-ids` 一次最多 100 个。
  private static final int BATCH = 100;

  private final PublicationEndpoint publications;
  private final PublicationNeighborEndpoint neighbors;

  /// 创建适配器。
  ///
  /// @param publications 文献解析接口代理
  /// @param neighbors 文献邻居接口代理
  public PublicationCatalogAdapter(
      PublicationEndpoint publications, PublicationNeighborEndpoint neighbors) {
    this.publications = publications;
    this.neighbors = neighbors;
  }

  /// 按 100 个一批核对：存在、`ACTIVE`、不撤稿才合格。
  ///
  /// @param ids 文献 ID
  /// @return 合格的
  @Override
  public Set<Long> eligibleAmong(Collection<Long> ids) {
    List<Long> all = List.copyOf(new HashSet<>(ids));
    Set<Long> eligible = new HashSet<>();
    for (int from = 0; from < all.size(); from += BATCH) {
      List<Long> batch = all.subList(from, Math.min(from + BATCH, all.size()));
      call(() -> publications.resolveByIds(PublicationResolveByIdsReq.of(batch))).items().stream()
          .filter(PublicationCatalogAdapter::eligible)
          .map(PublicationResolutionResp::id)
          .forEach(eligible::add);
    }
    return Set.copyOf(eligible);
  }

  @Override
  public List<Long> latestIds(int limit) {
    return call(() -> neighbors.latest(limit)).ids();
  }

  @Override
  public List<AlsoReadItem> subjectNeighbors(long articleId, int limit) {
    return call(() -> neighbors.subjectNeighbors(articleId, limit)).items().stream()
        .map(PublicationCatalogAdapter::toItem)
        .toList();
  }

  /// 合格 = 存在、没有软删、不撤稿。
  ///
  /// @param row 解析结果
  /// @return 合格为 true
  private static boolean eligible(PublicationResolutionResp row) {
    return row.exists() && row.state() == PublicationStateResp.ACTIVE && !row.retracted();
  }

  /// 邻居 → 看过还看了的一篇。
  ///
  /// @param resp 邻居
  /// @return 一篇
  private static AlsoReadItem toItem(SubjectNeighborResp resp) {
    return AlsoReadItem.of(
        resp.id(), AlsoReadSource.valueOf(resp.reason().name()), resp.meshTerm(), resp.venueName());
  }

  /// 调一次 catalog，失败包成不可用。
  ///
  /// @param call 调用
  /// @param <T> 响应类型
  /// @return 响应
  private static <T> T call(Supplier<T> call) {
    try {
      return call.get();
    } catch (RemoteCallException ex) {
      log.warn(
          "catalog 内部接口返回错误: httpStatus={}, errorCode={}", ex.getHttpStatus(), ex.getErrorCode());
      throw new InsightUnavailableException("catalog 返回错误", ex);
    } catch (ResourceAccessException | IllegalStateException ex) {
      log.warn("catalog 内部接口不可用: cause={}", ex.getClass().getSimpleName());
      throw new InsightUnavailableException("catalog 不可用", ex);
    }
  }
}
