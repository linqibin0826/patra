package dev.linqibin.patra.starter.security.forward;

import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import dev.linqibin.starter.httpinterface.interceptor.InternalCallInterceptor;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/// 内部客户端的拦截器：当前线程有带断言的用户时，把网关签的断言原样放进出站请求。
///
/// 只挂在 `RestClientFactory` 创建的内部客户端上，外部数据源的客户端不经过这里。
/// `CurrentUserRunner` 放进去的用户没有断言，不转发：离开请求，用户身份是数据，不是凭据。
public final class IdentityAssertionForwardingInterceptor implements InternalCallInterceptor {

  /// 有断言就写进 `Authorization: Bearer`，覆盖已有的同名头；没有就原样放行。
  ///
  /// @param request 出站请求
  /// @param body 请求体
  /// @param execution 后续的执行链
  /// @return 响应
  /// @throws IOException 后续执行失败时
  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    Authentication authentication =
        SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
    if (authentication instanceof CurrentUserAuthentication user) {
      String assertion = user.getCredentials();
      if (assertion != null) {
        request.getHeaders().setBearerAuth(assertion);
      }
    }
    return execution.execute(request, body);
  }
}
