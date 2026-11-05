package dev.patra.identity.adapter.account.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/// 什么都不存的已授权客户端仓库：确认身份后第三方令牌就丢弃（v0.19 决策 C、范围 E）。
///
/// 框架默认的实现把 access token 留在内存里；声明成 Bean 后 Boot 的默认实现让位。
public class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

  /// 永远没有。
  ///
  /// @param clientRegistrationId 注册 ID
  /// @param principal 当前认证
  /// @param request 请求
  /// @param <T> 类型
  /// @return `null`
  @Override
  public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
      String clientRegistrationId, Authentication principal, HttpServletRequest request) {
    return null;
  }

  /// 不存。
  ///
  /// @param authorizedClient 已授权客户端，带令牌
  /// @param principal 当前认证
  /// @param request 请求
  /// @param response 响应
  @Override
  public void saveAuthorizedClient(
      OAuth2AuthorizedClient authorizedClient,
      Authentication principal,
      HttpServletRequest request,
      HttpServletResponse response) {}

  /// 没东西可删。
  ///
  /// @param clientRegistrationId 注册 ID
  /// @param principal 当前认证
  /// @param request 请求
  /// @param response 响应
  @Override
  public void removeAuthorizedClient(
      String clientRegistrationId,
      Authentication principal,
      HttpServletRequest request,
      HttpServletResponse response) {}
}
