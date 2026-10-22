package dev.patra.identity.adapter.rest.users.response;

import dev.patra.identity.domain.model.read.PublicAudience;
import dev.patra.identity.domain.model.read.PublicUserView;

/// 公开身份卡的响应体。不含邮箱、ID、状态、任何设置。
///
/// @param username 用户名
/// @param displayName 昵称，没设时等于用户名
/// @param avatarUrl 头像地址，本版恒为 `null`（用默认头像）
/// @param audience 能被谁看到：`PUBLIC` 谁都能看，`SELF` 只有本人（门户据此提示「目前只有你能看到」）
public record PublicUserResponse(
    String username, String displayName, String avatarUrl, PublicAudience audience) {

  /// 从裁剪后的视图映射。
  ///
  /// @param view 公开主页视图
  /// @return 响应
  public static PublicUserResponse from(PublicUserView view) {
    return new PublicUserResponse(view.username(), view.displayName(), null, view.audience());
  }
}
