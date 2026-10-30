package dev.patra.identity.api.account.dto;

import java.util.List;

/// 批量收件信息的请求。不做防御性拷贝：`null` 与含 `null` 的列表要原样到达服务端校验（422）。
///
/// @param userIds 用户 ID，最多 100 个（按去重前的长度算），重复的保留第一次出现
public record UserRecipientsReq(List<Long> userIds) {

  /// 创建请求。
  ///
  /// @param userIds 用户 ID
  /// @return 请求
  public static UserRecipientsReq of(List<Long> userIds) {
    return new UserRecipientsReq(userIds);
  }
}
