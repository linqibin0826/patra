---
type: bug
bug_id: BUG-2025-001
date: 2025-11-27
severity: high
status: resolved
resolved_date: 2025-11-27
time_spent: ~2h
module: patra-spring-boot-starter-rest-client
tags:
  - record/bug
  - tech/spring-cloud
  - tech/loadbalancer
  - tech/restclient
---

# RestClient 外部 API 调用被 LoadBalancer 拦截器错误处理

## 现象

MeSH 导入任务调用外部 API (`nlmpubs.nlm.nih.gov`) 失败，报错：

```
Service Instance cannot be null, serviceId: nlmpubs.nlm.nih.gov
```

外部域名被当作 Nacos 服务 ID 解析。

## 原因

**依赖链**：
- `patra-catalog-boot` → `spring-cloud-starter-alibaba-nacos-discovery` → `spring-cloud-loadbalancer`
- `patra-spring-boot-starter-rest-client` → `spring-retry`

当 `spring-retry` + `spring-cloud-loadbalancer` 同时存在时，Spring Cloud 自动注册 `RetryLoadBalancerInterceptor` 作为 `ClientHttpRequestInterceptor` Bean。

`RestClientAutoConfiguration` 通过 `ObjectProvider<ClientHttpRequestInterceptor>` 注入所有拦截器，包括 LoadBalancer 拦截器，导致外部 URL 请求被拦截并尝试服务发现解析。

**请求链路**：
```
RestClient.get().uri("https://nlmpubs.nlm.nih.gov/...")
  → LoggingInterceptor
    → RetryLoadBalancerInterceptor  ← 问题所在
      → BlockingLoadBalancerClient.execute()
        → 尝试将域名解析为 Nacos 服务 ID → 失败
```

## 解决方案

在 `RestClientAutoConfiguration` 中过滤掉 LoadBalancer 拦截器：

```java
// 注入拦截器（按 @Order 排序），过滤掉 LoadBalancer 拦截器
builder.requestInterceptors(
    list ->
        list.addAll(
            interceptorsProvider.orderedStream()
                .filter(i -> !isLoadBalancerInterceptor(i))
                .toList()));

private boolean isLoadBalancerInterceptor(ClientHttpRequestInterceptor interceptor) {
  String className = interceptor.getClass().getName();
  return className.contains("LoadBalancer");
}
```

**为什么不影响 Feign**：Feign 使用独立的 HTTP 客户端栈（Apache HttpClient 5），有自己的拦截器机制（`feign.RequestInterceptor`），与 Spring 的 `ClientHttpRequestInterceptor` 完全独立。

## 相关

- commit: `96277402`
- 文件: `patra-spring-boot-starter-rest-client/.../RestClientAutoConfiguration.java`
