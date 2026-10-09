package dev.linqibin.patra.gateway.security;

import dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import java.util.Objects;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.function.ServerRequest;

/// 出站的 `Authorization`：外部的一律剥掉；已登录时写入网关现签的身份断言，匿名什么都不写。
///
/// 下游收到的 `Authorization` 只可能是网关签的断言，或者没有。每个请求现签、不缓存，
/// 登出和封禁删掉会话后，下一个请求就签不出断言。安全过滤器链放进线程上下文的认证对象，
/// 在 `DispatcherServlet` 调用本过滤器时还在。
public final class IdentityAssertionRequestHeadersFilter
    implements HttpHeadersFilter.RequestHttpHeadersFilter, Ordered {

  /// 排在框架的转发头过滤器之前；和 `Authorization` 无关的过滤器不会再碰这个头。
  static final int ORDER = -100;

  private final IdentityAssertionSigner signer;

  /// 创建过滤器。
  ///
  /// @param signer 用网关私钥构造的签名器
  public IdentityAssertionRequestHeadersFilter(IdentityAssertionSigner signer) {
    this.signer = Objects.requireNonNull(signer, "signer 不能为 null");
  }

  /// 返回换过 `Authorization` 的新副本。
  ///
  /// @param input 当前的出站请求头（只读视图）
  /// @param request 当前请求
  /// @return 新的可变请求头
  @Override
  public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
    HttpHeaders output = HttpHeaders.copyOf(input);
    output.remove(HttpHeaders.AUTHORIZATION);
    Authentication authentication =
        SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
    if (authentication instanceof CurrentUserAuthentication current) {
      output.setBearerAuth(signer.sign(current.getPrincipal()));
    }
    return output;
  }

  /// 顺序。
  ///
  /// @return {@link #ORDER}
  @Override
  public int getOrder() {
    return ORDER;
  }
}
