package dev.patra.identity.api.account.dto;

import java.util.List;

/// 批量用户摘要的请求。不做防御性拷贝：`null` 与含 `null` 的列表要原样到达服务端校验（422）。
///
/// @param userIds 用户 ID，最多 100 个（按去重前的长度算），重复的保留第一次出现
/// @param viewerUserId 访客的用户 ID，匿名或后台任务时为 `null`
public record UserSummariesReq(List<Long> userIds, Long viewerUserId) {

  /// 创建请求。
  ///
  /// @param userIds 用户 ID
  /// @param viewerUserId 访客的用户 ID，可为 `null`
  /// @return 请求
  public static UserSummariesReq of(List<Long> userIds, Long viewerUserId) {
    return new UserSummariesReq(userIds, viewerUserId);
  }
}
