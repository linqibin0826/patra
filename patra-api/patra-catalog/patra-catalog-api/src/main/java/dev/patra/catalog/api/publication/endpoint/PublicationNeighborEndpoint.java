package dev.patra.catalog.api.publication.endpoint;

import dev.patra.catalog.api.publication.dto.PublicationLatestResp;
import dev.patra.catalog.api.publication.dto.SubjectNeighborsResp;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/// 文献邻居内部 API 契约：analytics 的热读补位取最新收录、「看过还看了」补位取同主题邻居（PAP-170）。
///
/// 与 [PublicationEndpoint] 同前缀，单独一个接口：那边类上声明了请求体为 JSON，GET 会被要求带 JSON 请求体。
///
/// 端点：
///
/// - GET /_internal/publications/latest?limit= - 最新收录（1 ~ 100）
/// - GET /_internal/publications/{id}/subject-neighbors?limit= - 同主题邻居（1 ~ 20）
///
/// 「可用」= 没有软删、不是撤稿文献（发表类型 `D016441` 或全文原因 `RETRACTED`）。
///
/// @author linqibin
/// @since 0.1.0
@HttpExchange(url = "/_internal/publications", accept = "application/json")
public interface PublicationNeighborEndpoint {

  /// 最新收录的可用文献：主键倒序（首次入库时间倒序）。
  ///
  /// @param limit 条数，1 ~ 100
  /// @return 文献主键
  @GetExchange("/latest")
  PublicationLatestResp latest(@RequestParam("limit") @Min(1) @Max(100) int limit);

  /// 同主题邻居：先同主要主题词（PubMed `[majr]`，多个词轮流取），再同期刊；收录时间新的在前，去掉自己与重复。
  /// 文献不存在或已软删时 `items` 为空。
  ///
  /// @param id 文献主键
  /// @param limit 条数，1 ~ 20
  /// @return 邻居，按展示顺序
  @GetExchange("/{id}/subject-neighbors")
  SubjectNeighborsResp subjectNeighbors(
      @PathVariable("id") @Positive long id, @RequestParam("limit") @Min(1) @Max(20) int limit);
}
