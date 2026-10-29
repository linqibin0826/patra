package dev.patra.identity.adapter.session.rest.response;

import dev.patra.identity.app.session.usecase.query.SessionView;
import java.util.List;

/// 登录设备列表：本机第一，其余按最后活跃倒序。
///
/// @param sessions 列表
public record SessionListResponse(List<SessionResponse> sessions) {

  /// 拷贝列表。
  public SessionListResponse {
    sessions = sessions == null ? List.of() : List.copyOf(sessions);
  }

  /// 从查询结果映射。
  ///
  /// @param views 查询结果
  /// @return 响应
  public static SessionListResponse from(List<SessionView> views) {
    return new SessionListResponse(views.stream().map(SessionResponse::from).toList());
  }
}
