package dev.patra.interaction.adapter.discussion.rest.response;

import java.util.List;

/// @ 联想的结果：参与者在前，合计最多 8 位。
///
/// @param items 联想项
public record SuggestionsResponse(List<SuggestionResponse> items) {

  /// 防御性拷贝。
  public SuggestionsResponse {
    items = List.copyOf(items);
  }

  /// 创建。
  ///
  /// @param items 联想项
  /// @return 响应
  public static SuggestionsResponse of(List<SuggestionResponse> items) {
    return new SuggestionsResponse(items);
  }
}
