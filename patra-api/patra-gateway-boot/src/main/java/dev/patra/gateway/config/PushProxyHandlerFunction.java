package dev.patra.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchangeHandlerFunction;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import reactor.netty.resources.ConnectionProvider;

/// 推送路由（`lb-push://`）的处理器：把请求交给一个用推送专用连接池的代理处理器（PAP-145）。
///
/// 内部那个 `ProxyExchangeHandlerFunction` 不能声明成 Bean（理由见 [PushRouteConfiguration]），因此收不到
/// 容器刷新事件；收不到时它不收集出站头过滤器，会原样转发全部请求头，浏览器带的会话令牌就直达 notification。
/// 这个 Bean 收下刷新事件转交给它；停机开始时结束正在转发的推送流，销毁时释放推送连接池。
@RequiredArgsConstructor
public class PushProxyHandlerFunction
    implements HandlerFunction<ServerResponse>,
        ApplicationListener<ContextRefreshedEvent>,
        DisposableBean {

  /// 用推送专用 `RestClient` 转发的代理处理器。
  private final ProxyExchangeHandlerFunction delegate;

  /// 内部处理器用的代理交换，停机时由它结束正在转发的推送流。
  private final PushProxyExchange exchange;

  /// 推送专用连接池，归这个 Bean 管。
  private final ConnectionProvider pool;

  /// 转发一个推送请求。
  ///
  /// @param request 已经过负载均衡过滤器（请求地址指向选中的实例）、剥过前缀的请求
  /// @return 代理的响应
  @Override
  public ServerResponse handle(ServerRequest request) throws Exception {
    return delegate.handle(request);
  }

  /// 容器刷新后让内部处理器收集出站头过滤器（剥外部 `Authorization`、写身份断言、`X-Forwarded-*`）。
  ///
  /// @param event 容器刷新事件
  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    delegate.onApplicationEvent(event);
  }

  /// 停机开始时（关闭事件先于优雅停机的等待）结束正在转发的推送流，优雅停机不用等它们。
  @EventListener(ContextClosedEvent.class)
  public void closeOpenStreams() {
    exchange.closeOpenStreams();
  }

  /// 销毁时关掉推送连接池里的连接。
  @Override
  public void destroy() {
    pool.dispose();
  }
}
