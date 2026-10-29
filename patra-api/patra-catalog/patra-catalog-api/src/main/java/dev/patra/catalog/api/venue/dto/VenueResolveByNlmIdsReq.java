package dev.patra.catalog.api.venue.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/// 按 NLM ID 批量解析期刊的请求。
///
/// NLM ID 一律按字符串处理、保留前导零；写法是 1 ~ 19 位数字加可选的一个大写字母后缀
/// （mini 上见过 7 ~ 9 位、16 位新式 ID，后缀 `R` / `A`），区分大小写。主 NLM、副 NLM 都可以。
///
/// @param nlmIds NLM ID，1 ~ 100 个
/// @author linqibin
/// @since 0.1.0
public record VenueResolveByNlmIdsReq(
    @NotEmpty(message = "nlmIds 不能为空") @Size(max = 100, message = "nlmIds 最多 100 个")
        List<@Pattern(regexp = "^[0-9]{1,19}[A-Z]?$", message = "nlmId 格式不对") String> nlmIds) {

  /// 紧凑构造器：防御性拷贝；`null` 原样保留交给 `@NotEmpty`（含 `null` 元素时拷贝失败，按消息不可读处理）。
  public VenueResolveByNlmIdsReq {
    nlmIds = nlmIds != null ? List.copyOf(nlmIds) : null;
  }

  /// 创建请求。
  ///
  /// @param nlmIds NLM ID
  /// @return 请求
  public static VenueResolveByNlmIdsReq of(List<String> nlmIds) {
    return new VenueResolveByNlmIdsReq(nlmIds);
  }
}
