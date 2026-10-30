package dev.patra.gateway;

import dev.patra.tool.launch.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// Patra API 网关主入口。
///
/// Spring Cloud Gateway 的 WebMVC 版（servlet 栈），为所有 Patra 微服务提供统一入口：
///
/// - 按路径前缀把请求路由到下游微服务（patra-catalog、patra-registry、patra-identity、patra-interaction），
///   经 Nacos 发现、LoadBalancer 选实例
/// - 鉴权：按会话令牌查 Redis 会话并续期，按路径规则放行或拒绝，通过后把网关签的身份断言写进转发请求
/// - 代理走 Boot 的 `RestClient`（JDK HttpClient），每个请求一个虚拟线程
/// - 网关自身的错误输出 ProblemDetail（前缀 `GW`）；下游的响应原样透传
/// - 聚合各服务的 OpenAPI 文档（Scalar UI）
///
/// 默认端口 9528。路由与客户端配置见 `application.yml`，随环境变的值在 Nacos（`patra-common.yaml`、
/// `patra-gateway.yaml`），设计见 `docs/patra/specs/2026-10-09-gateway-webmvc-design.md` 与
/// `docs/patra/specs/2026-10-09-gateway-auth-design.md`。
@SpringBootApplication
public class PatraGatewayApplication {

  /// 经启动器启动应用，服务名 `patra-gateway`。
  ///
  /// @param args 传递给应用的命令行参数
  public static void main(String[] args) {
    PatraApplication.run("patra-gateway", PatraGatewayApplication.class, args);
  }
}
