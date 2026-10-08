package dev.linqibin.starter.httpinterface.interceptor;

import org.springframework.http.client.ClientHttpRequestInterceptor;

/// 只挂到内部服务客户端上的请求拦截器。
///
/// `RestClientCustomizer` 作用于容器里每一个 RestClient.Builder，包括调外部数据源的客户端；
/// 实现本接口的 Bean 只会被 `RestClientFactory` 加到它创建的内部客户端上。安全 starter 用它
/// 转发身份断言。没有新方法，只是标记。
public interface InternalCallInterceptor extends ClientHttpRequestInterceptor {}
