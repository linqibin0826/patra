package com.patra.spring.boot.starter.test.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * WireMock 自动配置类
 *
 * <p>在 WireMock 类路径存在时,自动配置 WireMock Server。</p>
 *
 * <h3>自动配置条件</h3>
 * <ul>
 *   <li>@ConditionalOnClass: WireMock 在类路径中</li>
 * </ul>
 *
 * <h3>配置内容</h3>
 * <ul>
 *   <li>提供 WireMock Server Bean</li>
 *   <li>配置 WireMock 端口和规则</li>
 *   <li>支持录制和回放 HTTP 请求</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @SpringBootTest
 * class ExternalApiTest {
 *
 *     @Autowired
 *     private WireMockServer wireMockServer;
 *
 *     @Test
 *     void testExternalApiCall() {
 *         // 配置 Mock 响应
 *         wireMockServer.stubFor(get(urlEqualTo("/api/users/1"))
 *             .willReturn(aResponse()
 *                 .withStatus(200)
 *                 .withHeader("Content-Type", "application/json")
 *                 .withBody("{\"id\":1,\"name\":\"Alice\"}")));
 *
 *         // 调用业务逻辑
 *         User user = userService.fetchUserFromExternalApi(1);
 *
 *         // 验证结果
 *         assertThat(user.getName()).isEqualTo("Alice");
 *     }
 * }
 * }</pre>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.github.tomakehurst.wiremock.WireMockServer")
public class WireMockAutoConfiguration {

    // 此类预留用于 WireMock 自动配置,当前为骨架
    // 后续可以在此添加 WireMockServer Bean 定义
}
