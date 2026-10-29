package dev.patra.catalog.api.publication.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/// 按 PMID 批量解析文献的请求。
///
/// @param pmids PubMed ID，1 ~ 100 个，每个为 1 ~ 15 位且不以 0 开头的数字
/// @author linqibin
/// @since 0.1.0
public record PublicationResolveByPmidsReq(
    @NotEmpty(message = "pmids 不能为空") @Size(max = 100, message = "pmids 最多 100 个")
        List<@Pattern(regexp = "^[1-9][0-9]{0,14}$", message = "pmid 格式不对") String> pmids) {

  /// 紧凑构造器：防御性拷贝；`null` 原样保留交给 `@NotEmpty`（含 `null` 元素时拷贝失败，按消息不可读处理）。
  public PublicationResolveByPmidsReq {
    pmids = pmids != null ? List.copyOf(pmids) : null;
  }

  /// 创建请求。
  ///
  /// @param pmids PubMed ID
  /// @return 请求
  public static PublicationResolveByPmidsReq of(List<String> pmids) {
    return new PublicationResolveByPmidsReq(pmids);
  }
}
