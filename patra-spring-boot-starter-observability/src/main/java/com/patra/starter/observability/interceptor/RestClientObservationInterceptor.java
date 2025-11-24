package com.patra.starter.observability.interceptor;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * REST 客户端可观测性拦截器。
 *
 * <p>功能：
 * <ul>
 *   <li>为 HTTP 请求创建 Observation</li>
 *   <li>自动记录请求方法、URI、状态码等关键信息</li>
 *   <li>与 Micrometer Observation 集成，自动生成追踪和指标</li>
 * </ul>
 *
 * <p>实现模式：
 * <ul>
 *   <li>在请求执行前创建并启动 Observation</li>
 *   <li>执行 HTTP 请求</li>
 *   <li>记录响应状态码</li>
 *   <li>停止 Observation（成功或失败）</li>
 * </ul>
 *
 * <p>Observation 标签：
 * <ul>
 *   <li>http.method - HTTP 请求方法（GET、POST 等）</li>
 *   <li>http.uri - 请求 URI（不含查询参数，避免高基数）</li>
 *   <li>http.status_code - HTTP 响应状态码</li>
 *   <li>http.outcome - 请求结果（SUCCESS、CLIENT_ERROR、SERVER_ERROR、UNKNOWN）</li>
 * </ul>
 *
 * <p>使用场景：
 * <ul>
 *   <li>监控 REST 客户端请求的性能和成功率</li>
 *   <li>自动集成分布式追踪（与 Sleuth/Zipkin 集成）</li>
 *   <li>生成 HTTP 客户端指标（通过 DefaultMeterObservationHandler）</li>
 * </ul>
 *
 * @author Jobs
 * @since 1.0.0
 */
public class RestClientObservationInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RestClientObservationInterceptor.class);

    private static final String OBSERVATION_NAME = "http.client.requests";

    private final ObservationRegistry observationRegistry;

    /**
     * 构造函数。
     *
     * @param observationRegistry Observation 注册中心
     */
    public RestClientObservationInterceptor(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
        log.info("初始化 REST 客户端可观测性拦截器");
    }

    /**
     * 拦截 HTTP 请求。
     *
     * <p>流程：
     * <ol>
     *   <li>创建 Observation 并添加请求信息标签</li>
     *   <li>启动 Observation</li>
     *   <li>执行 HTTP 请求</li>
     *   <li>记录响应状态码和结果</li>
     *   <li>停止 Observation</li>
     *   <li>如果发生异常，记录错误事件</li>
     * </ol>
     *
     * @param request   HTTP 请求
     * @param body      请求体
     * @param execution 请求执行器
     * @return HTTP 响应
     * @throws IOException 请求执行异常
     */
    @Override
    public ClientHttpResponse intercept(
        HttpRequest request,
        byte[] body,
        ClientHttpRequestExecution execution
    ) throws IOException {
        // 创建 Observation
        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry);

        // 添加低基数标签（请求信息）
        observation.lowCardinalityKeyValue("http.method", request.getMethod().name());
        observation.lowCardinalityKeyValue("http.uri", extractPath(request.getURI().toString()));

        // 启动 Observation
        observation.start();

        try {
            // 执行 HTTP 请求
            ClientHttpResponse response = execution.execute(request, body);

            // 记录响应状态码
            int statusCode = response.getStatusCode().value();
            observation.lowCardinalityKeyValue("http.status_code", String.valueOf(statusCode));
            observation.lowCardinalityKeyValue("http.outcome", determineOutcome(statusCode));

            log.debug("HTTP 请求完成: {} {} -> {}", request.getMethod(), request.getURI(), statusCode);

            return response;

        } catch (IOException e) {
            // 记录异常
            observation.lowCardinalityKeyValue("http.outcome", "UNKNOWN");
            observation.error(e);

            log.error("HTTP 请求失败: {} {}", request.getMethod(), request.getURI(), e);

            throw e;

        } finally {
            // 停止 Observation（无论成功或失败）
            observation.stop();
        }
    }

    /**
     * 提取请求路径（移除查询参数，避免高基数）。
     *
     * <p>示例：
     * <ul>
     *   <li>https://api.example.com/users/123?token=xxx → /users/123</li>
     *   <li>http://localhost:8080/api/v1/data → /api/v1/data</li>
     * </ul>
     *
     * @param uri 完整 URI
     * @return 请求路径（不含查询参数）
     */
    private String extractPath(String uri) {
        try {
            // 移除查询参数
            int queryIndex = uri.indexOf('?');
            if (queryIndex > 0) {
                uri = uri.substring(0, queryIndex);
            }

            // 提取路径部分
            int pathStart = uri.indexOf('/', uri.indexOf("://") + 3);
            if (pathStart > 0) {
                return uri.substring(pathStart);
            }

            return uri;
        } catch (Exception e) {
            log.warn("无法解析 URI: {}", uri, e);
            return "UNKNOWN";
        }
    }

    /**
     * 根据状态码判断请求结果。
     *
     * @param statusCode HTTP 状态码
     * @return 请求结果（SUCCESS、CLIENT_ERROR、SERVER_ERROR）
     */
    private String determineOutcome(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return "SUCCESS";
        } else if (statusCode >= 400 && statusCode < 500) {
            return "CLIENT_ERROR";
        } else if (statusCode >= 500) {
            return "SERVER_ERROR";
        } else {
            return "UNKNOWN";
        }
    }
}
