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
/// 网关查到会话后构造它，下游从请求头解析后构造它，两边用同一个类型。
/// 本版没有角色，权限列表为空；做 admin 时由建立认证的一方把角色传进来。
public final class CurrentUserAuthentication extends AbstractAuthenticationToken {

  @Serial private static final long serialVersionUID = 1L;

  private final CurrentUser principal;

  /// 创建没有任何权限的认证对象。
  ///
  /// @param principal 当前用户
  public CurrentUserAuthentication(CurrentUser principal) {
    this(principal, AuthorityUtils.NO_AUTHORITIES);
  }

  /// 创建带权限列表的认证对象。
  ///
  /// @param principal 当前用户
  /// @param authorities 权限列表
  public CurrentUserAuthentication(
      CurrentUser principal, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = Objects.requireNonNull(principal, "principal 不能为 null");
    super.setAuthenticated(true);
  }

  /// 没有凭据：身份已经由网关验证过。
  ///
  /// @return 始终为 `null`
  @Override
  public Object getCredentials() {
    return null;
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
