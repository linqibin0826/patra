package dev.patra.catalog.domain.fulltext.model.read;

import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import java.util.List;
import lombok.Builder;

/// 一个待解析的全文记录。
///
/// @param publicationId 文献 ID
/// @param pmcid PMCID
/// @param pmcVersion 版本前缀，XML 对象名为 `{pmcVersion}.xml`
/// @param objectPrefix 对象前缀
/// @param media 已下载的图片清单
/// @author linqibin
/// @since 0.11.0
@Builder
public record ParseCandidate(
    long publicationId,
    String pmcid,
    String pmcVersion,
    String objectPrefix,
    List<FullTextMedia> media) {

  /// 防御性拷贝，`null` 视为空。
  public ParseCandidate {
    media = media != null ? List.copyOf(media) : List.of();
  }

  /// XML 的对象键。
  ///
  /// @return 对象键
  public String xmlObjectKey() {
    return objectPrefix + pmcVersion + ".xml";
  }
}
