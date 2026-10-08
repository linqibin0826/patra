package dev.linqibin.patra.starter.security.authentication;

import dev.linqibin.patra.common.security.CurrentUser;
import java.io.Serial;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/// 主体是 `CurrentUser` 的认证对象，构造出来就是已认证状态。
///
/// 下游验签后构造它，凭据是原始断言，内部客户端替用户调用时原样转发；网关查到会话后
/// 和 `CurrentUserRunner` 构造它时没有断言，凭据为 `null`。
/// 本版没有角色，权限列表为空；做 admin 时由建立认证的一方把角色传进来。
public final class CurrentUserAuthentication extends AbstractAuthenticationToken {

  @Serial private static final long serialVersionUID = 1L;

  private final CurrentUser principal;
  private final String assertion;

  /// 创建没有凭据、没有权限的认证对象。
  ///
  /// @param principal 当前用户
  public CurrentUserAuthentication(CurrentUser principal) {
    this(principal, null, AuthorityUtils.NO_AUTHORITIES);
  }

  /// 创建带原始断言的认证对象。
  ///
  /// @param principal 当前用户
  /// @param assertion 验签通过的断言，紧凑序列化
  public CurrentUserAuthentication(CurrentUser principal, String assertion) {
    this(principal, assertion, AuthorityUtils.NO_AUTHORITIES);
  }

  /// 创建带权限列表、没有凭据的认证对象。
  ///
  /// @param principal 当前用户
  /// @param authorities 权限列表
  public CurrentUserAuthentication(
      CurrentUser principal, Collection<? extends GrantedAuthority> authorities) {
    this(principal, null, authorities);
  }

  /// 创建认证对象。
  ///
  /// @param principal 当前用户
  /// @param assertion 原始断言，可以为 `null`
  /// @param authorities 权限列表
  private CurrentUserAuthentication(
      CurrentUser principal, String assertion, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = Objects.requireNonNull(principal, "principal 不能为 null");
    this.assertion = assertion;
    super.setAuthenticated(true);
  }

  /// 原始断言；没有断言时为 `null`。父类的 `toString()` 把它打成 `[PROTECTED]`。
  ///
  /// @return 紧凑序列化的断言或 `null`
  @Override
  public String getCredentials() {
    return assertion;
  }

  /// 返回当前用户。
  ///
  /// @return 当前用户
  @Override
  public CurrentUser getPrincipal() {
    return principal;
  }

  /// 只返回用户 ID。父类的默认实现会把整条记录打进日志。
  ///
  /// @return 用户 ID 的十进制字符串
  @Override
  public String getName() {
    return Long.toString(principal.userId());
  }
}
