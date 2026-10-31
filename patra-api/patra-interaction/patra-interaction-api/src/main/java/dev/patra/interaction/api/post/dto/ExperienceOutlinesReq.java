package dev.patra.interaction.api.post.dto;

import java.util.List;

/// 批量取经验结果的请求。不做防御性拷贝：`null` 与含 `null` 的列表要原样到达服务端校验（422）。
///
/// @param postIds 帖子 ID，最多 100 个（按去重前的长度算），重复的保留第一次出现
public record ExperienceOutlinesReq(List<Long> postIds) {

  /// 创建请求。
  ///
  /// @param postIds 帖子 ID
  /// @return 请求
  public static ExperienceOutlinesReq of(List<Long> postIds) {
    return new ExperienceOutlinesReq(postIds);
  }
}
