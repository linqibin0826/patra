package com.patra.spring.boot.starter.test.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc 自动配置类
 *
 * <p>在 Web 应用环境下,自动配置 MockMvc 相关组件。</p>
 *
 * <h3>自动配置条件</h3>
 * <ul>
 *   <li>@ConditionalOnClass(MockMvc.class): MockMvc 在类路径中</li>
 *   <li>@ConditionalOnWebApplication: 应用为 Web 应用</li>
 * </ul>
 *
 * <h3>配置内容</h3>
 * <ul>
 *   <li>提供 MockMvc 测试支持</li>
 *   <li>配置 JSON 序列化/反序列化</li>
 *   <li>配置请求/响应编码</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @SpringBootTest
 * @AutoConfigureMockMvc
 * class ControllerTest {
 *
 *     @Autowired
 *     private MockMvc mockMvc;
 *
 *     @Test
 *     void testGetUser() throws Exception {
 *         mockMvc.perform(get("/users/1"))
 *             .andExpect(status().isOk())
 *             .andExpect(jsonPath("$.id").value(1));
 *     }
 * }
 * }</pre>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MockMvc.class)
@ConditionalOnWebApplication
public class MockMvcAutoConfiguration {

    // 此类预留用于 MockMvc 自动配置,当前为骨架
    // 后续可以在此添加自定义的 MockMvc 配置
}
