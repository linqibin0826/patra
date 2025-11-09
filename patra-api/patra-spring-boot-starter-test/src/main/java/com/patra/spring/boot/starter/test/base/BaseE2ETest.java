package com.patra.spring.boot.starter.test.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.spring.boot.starter.test.config.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * E2E 测试基类
 *
 * <p>启动完整的 Spring Boot 应用,使用 MockMvc 模拟 HTTP 请求,验证完整的业务流程。
 * 所有 E2E 测试类都应该继承此基类。</p>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>完整应用启动: 使用 RANDOM_PORT 模式</li>
 *   <li>MockMvc 支持: 模拟 HTTP 请求/响应</li>
 *   <li>自动启动 TestContainers: MySQL, Redis, Nacos</li>
 *   <li>JSON 序列化/反序列化: 自动配置 ObjectMapper</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * class RegistryFlowE2E extends BaseE2ETest {
 *
 *     @Test
 *     void testCreateAndQueryProvenance() throws Exception {
 *         // 1. 创建 Provenance
 *         ProvenanceRequest request = new ProvenanceRequest("PubMed", "https://pubmed.ncbi.nlm.nih.gov");
 *
 *         MvcResult createResult = performPost("/api/v1/provenances", request)
 *             .andExpect(status().isCreated())
 *             .andExpect(jsonPath("$.id").exists())
 *             .andReturn();
 *
 *         String id = extractJsonValue(createResult, "$.id");
 *
 *         // 2. 查询 Provenance
 *         performGet("/api/v1/provenances/{id}", id)
 *             .andExpect(status().isOk())
 *             .andExpect(jsonPath("$.name").value("PubMed"));
 *
 *         // 3. 删除 Provenance
 *         performDelete("/api/v1/provenances/{id}", id)
 *             .andExpect(status().isNoContent());
 *     }
 * }
 * }</pre>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "logging.level.com.patra.test=DEBUG"
})
public abstract class BaseE2ETest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * 执行 GET 请求
     *
     * @param url URL 路径
     * @param uriVars URI 变量
     * @return ResultActions
     * @throws Exception 如果请求失败
     */
    protected ResultActions performGet(String url, Object... uriVars) throws Exception {
        return mockMvc.perform(
            MockMvcRequestBuilders.get(url, uriVars)
                .accept(MediaType.APPLICATION_JSON)
        );
    }

    /**
     * 执行 POST 请求
     *
     * @param url URL 路径
     * @param body 请求体
     * @return ResultActions
     * @throws Exception 如果请求失败
     */
    protected ResultActions performPost(String url, Object body) throws Exception {
        return mockMvc.perform(
            MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        );
    }

    /**
     * 执行 PUT 请求
     *
     * @param url URL 路径
     * @param body 请求体
     * @return ResultActions
     * @throws Exception 如果请求失败
     */
    protected ResultActions performPut(String url, Object body) throws Exception {
        return mockMvc.perform(
            MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        );
    }

    /**
     * 执行 DELETE 请求
     *
     * @param url URL 路径
     * @param uriVars URI 变量
     * @return ResultActions
     * @throws Exception 如果请求失败
     */
    protected ResultActions performDelete(String url, Object... uriVars) throws Exception {
        return mockMvc.perform(
            MockMvcRequestBuilders.delete(url, uriVars)
        );
    }

    /**
     * 从响应中提取 JSON 字段值
     *
     * <p>使用 JsonPath 表达式提取字段值。</p>
     *
     * @param mvcResult MvcResult
     * @param jsonPath JSON 路径表达式 (例如: "$.id", "$.user.name")
     * @return 字段值字符串
     * @throws Exception 如果提取失败
     */
    protected String extractJsonValue(MvcResult mvcResult, String jsonPath) throws Exception {
        String content = mvcResult.getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(content, jsonPath).toString();
    }
}
