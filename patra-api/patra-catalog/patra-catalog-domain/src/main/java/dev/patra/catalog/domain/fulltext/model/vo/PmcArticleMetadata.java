package dev.patra.catalog.domain.fulltext.model.vo;

import java.util.List;
import lombok.Builder;

/// PMC 桶里一个版本的元数据（`{prefix}/{prefix}.json`）。
///
/// @param pmcid PMCID
/// @param versionPrefix 版本前缀，如 `PMC1234567.2`
/// @param openAccess 是否在 PMC 开放获取子集（`is_pmc_openaccess`）
/// @param manuscript 是否作者手稿
/// @param retracted PMC 是否标记撤稿
/// @param licenseCode 许可原值，可空
/// @param xml JATS XML 文件
/// @param pdf PDF 文件，没有时为空
/// @param media 正文图片（jpg / jpeg / png / gif / webp），补充材料等其他文件已排除
/// @author linqibin
/// @since 0.11.0
@Builder
public record PmcArticleMetadata(
    String pmcid,
    String versionPrefix,
    boolean openAccess,
    boolean manuscript,
    boolean retracted,
    String licenseCode,
    PmcArticleFile xml,
    PmcArticleFile pdf,
    List<PmcArticleFile> media) {

  /// 防御性拷贝 `media`，`null` 视为空。
  public PmcArticleMetadata {
    media = media != null ? List.copyOf(media) : List.of();
  }

  /// 版本号（前缀点号后的整数），用于同等条件下的平局规则。
  ///
  /// @return 版本号，解析不出时为 0
  public int versionNumber() {
    int dot = versionPrefix.lastIndexOf('.');
    try {
      return Integer.parseInt(versionPrefix.substring(dot + 1));
    } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
      return 0;
    }
  }
}
