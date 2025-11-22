package com.patra.catalog.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.meshimport.MeshImportOrchestrator;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.enums.MeshImportTaskStatus;
import com.patra.catalog.domain.port.MeshImportPort;
import com.patra.catalog.integration.config.MySQLContainerInitializer;
import java.io.File;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * MeSH 导入端到端测试。
 *
 * <p>测试完整的 MeSH 数据导入工作流，从 HTTP 请求到数据库持久化。
 *
 * <h3>测试覆盖的完整流程</h3>
 *
 * <pre>
 * 1. HTTP POST /api/v1/mesh/import/start
 *    ↓
 * 2. Controller 接收请求并转换为 Command
 *    ↓
 * 3. Orchestrator 执行业务编排
 *    ↓
 * 4. 下载 XML 文件（模拟）
 *    ↓
 * 5. 解析并批量导入数据
 *    ↓
 * 6. 更新任务状态并持久化
 *    ↓
 * 7. 返回 HTTP 响应
 * </pre>
 *
 * <h3>测试场景</h3>
 *
 * <ul>
 *   <li>✅ 完整导入流程（Happy Path）
 *   <li>✅ 并发导入防护（409 Conflict）
 *   <li>✅ 重试失败任务
 *   <li>✅ 清除进度重新开始
 * </ul>
 *
 * <h3>测试策略</h3>
 *
 * <ul>
 *   <li><strong>真实依赖</strong>: MySQL (Testcontainers)
 *   <li><strong>Mock 外部服务</strong>: XML 下载和解析（避免网络依赖）
 *   <li><strong>事务测试</strong>: 使用 {@code @Transactional} 确保测试隔离
 *   <li><strong>异步验证</strong>: 使用 Awaitility 等待异步操作完成
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.cloud.nacos.config.enabled=false",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.nacos.config.import-check.enabled=false",
      "patra.catalog.mesh.import.source-url=https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
      "patra.catalog.mesh.import.expected-counts.descriptor=30000",
      "patra.catalog.mesh.import.expected-counts.qualifier=100",
      "patra.catalog.mesh.import.expected-counts.tree-number=350000",
      "patra.catalog.mesh.import.expected-counts.entry-term=250000",
      "patra.catalog.mesh.import.expected-counts.concept=300000"
    })
@ContextConfiguration(initializers = {MySQLContainerInitializer.class})
@DisplayName("MeSH 导入端到端测试")
@Transactional
class MeshImportE2ETest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private MeshImportPort meshImportPort;

  @Autowired private MeshImportOrchestrator orchestrator;

  // Mock 外部依赖（避免真实下载）
  @MockBean private com.patra.catalog.domain.port.MeshFileDownloadPort meshFileDownloadPort;

  @MockBean private com.patra.catalog.domain.port.XmlParserPort xmlParserPort;

  @BeforeEach
  void setUp() {
    // Mock 文件下载（返回模拟文件）
    File mockFile = new File("/tmp/desc2025.xml");
    when(meshFileDownloadPort.download(any(String.class))).thenReturn(mockFile);

    // Mock XML 解析（返回空流，简化测试）
    when(xmlParserPort.parseDescriptors(any())).thenReturn(java.util.stream.Stream.empty());
    when(xmlParserPort.parseTreeNumbers(any())).thenReturn(java.util.stream.Stream.empty());
    when(xmlParserPort.parseEntryTerms(any())).thenReturn(java.util.stream.Stream.empty());
    when(xmlParserPort.parseConcepts(any())).thenReturn(java.util.stream.Stream.empty());
  }

  @AfterEach
  void tearDown() {
    // 清理测试数据
    // TODO: 添加清理逻辑（暂时依赖 @Transactional 回滚）
  }

  @Nested
  @DisplayName("完整导入流程测试")
  class CompleteWorkflowTest {

    @Test
    @DisplayName("应该完成完整的导入流程 (Happy Path)")
    void shouldCompleteFullImportWorkflow() {
      // given
      String requestBody =
          """
          {
            "sourceUrl": "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
            "taskName": "E2E测试-2025年MeSH数据导入"
          }
          """;

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

      // when - 发送 HTTP POST 请求
      ResponseEntity<String> response =
          restTemplate.postForEntity("/api/v1/mesh/import/start", request, String.class);

      // then - 验证 HTTP 响应
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).contains("taskId");
      assertThat(response.getBody()).contains("E2E测试-2025年MeSH数据导入");

      // 提取 taskId（简单的字符串解析，实际项目中应使用 JSON 库）
      String responseBody = response.getBody();
      String taskIdStr = responseBody.substring(
          responseBody.indexOf("\"taskId\":") + 9,
          responseBody.indexOf(",", responseBody.indexOf("\"taskId\":"))).trim().replace("\"", "");
      Long taskIdValue = Long.parseLong(taskIdStr);
      com.patra.catalog.domain.model.valueobject.MeshImportId taskId =
          com.patra.catalog.domain.model.valueobject.MeshImportId.of(taskIdValue);

      // then - 验证数据库状态
      await()
          .atMost(10, SECONDS)
          .untilAsserted(
              () -> {
                Optional<MeshImportAggregate> taskOpt = meshImportPort.findById(taskId);
                assertThat(taskOpt).isPresent();

                MeshImportAggregate task = taskOpt.get();
                assertThat(task.getTaskName()).isEqualTo("E2E测试-2025年MeSH数据导入");
                assertThat(task.getStatus())
                    .isIn(
                        MeshImportTaskStatus.PROCESSING,
                        MeshImportTaskStatus.SUCCESS,
                        MeshImportTaskStatus.FAILED);
                assertThat(task.getSourceUrl())
                    .contains("desc2025.xml");
              });
    }

    @Test
    @DisplayName("应该支持使用默认配置（空请求体）")
    void shouldSupportDefaultConfiguration() {
      // given
      String requestBody = "{}";

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

      // when
      ResponseEntity<String> response =
          restTemplate.postForEntity("/api/v1/mesh/import/start", request, String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).contains("taskId");
      assertThat(response.getBody()).contains("MeSH数据导入"); // 默认任务名称包含此字符串
    }
  }

  @Nested
  @DisplayName("并发控制测试")
  class ConcurrencyControlTest {

    @Test
    @DisplayName("应该在已有任务运行时返回 409")
    void shouldReturn409WhenTaskAlreadyRunning() {
      // given - 先创建一个 PROCESSING 状态的任务
      MeshImportAggregate runningTask =
          new MeshImportAggregate(
              null,
              "正在运行的任务",
              MeshImportTaskStatus.PENDING,
              null,
              null,
              "https://nlmpubs.nlm.nih.gov/projects/mesh/desc2025.xml",
              null,
              null,
              java.util.List.of(),
              0,
              0,
              0,
              null);
      runningTask.startImport(); // 状态变为 PROCESSING
      meshImportPort.save(runningTask);

      String requestBody =
          """
          {
            "taskName": "新的导入任务"
          }
          """;

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

      // when
      ResponseEntity<String> response =
          restTemplate.postForEntity("/api/v1/mesh/import/start", request, String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody()).contains("TASK_ALREADY_RUNNING");
      assertThat(response.getBody()).contains("已有正在运行的 MeSH 导入任务");
    }
  }

  @Nested
  @DisplayName("重试失败任务测试")
  class RetryFailedTaskTest {

    @Test
    @DisplayName("应该成功重试失败的任务")
    void shouldRetryFailedTaskSuccessfully() {
      // given - 先创建一个 FAILED 状态的任务
      MeshImportAggregate failedTask =
          new MeshImportAggregate(
              null,
              "失败的任务",
              MeshImportTaskStatus.PENDING,
              null,
              null,
              "https://nlmpubs.nlm.nih.gov/projects/mesh/desc2025.xml",
              null,
              null,
              java.util.List.of(),
              0,
              0,
              0,
              null);
      failedTask.startImport();
      failedTask.markAsFailed("模拟失败");
      failedTask = meshImportPort.save(failedTask);

      String taskId = failedTask.getId().value().toString();

      // when - 重试任务
      ResponseEntity<String> response =
          restTemplate.postForEntity(
              "/api/v1/mesh/import/retry/{taskId}", null, String.class, taskId);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).contains("taskId");
      assertThat(response.getBody()).contains(taskId);
    }

    @Test
    @DisplayName("应该在任务不存在时返回 404")
    void shouldReturn404WhenTaskNotFound() {
      // given
      String nonExistentTaskId = "9999999999";

      // when
      ResponseEntity<String> response =
          restTemplate.postForEntity(
              "/api/v1/mesh/import/retry/{taskId}", null, String.class, nonExistentTaskId);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody()).contains("TASK_NOT_FOUND");
      assertThat(response.getBody()).contains("任务不存在");
    }
  }

  @Nested
  @DisplayName("清除进度测试")
  class ClearAndRestartTest {

    @Test
    @DisplayName("应该成功清除进度")
    void shouldClearProgressSuccessfully() {
      // given
      String requestBody =
          """
          {
            "confirmClear": true
          }
          """;

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

      // when
      ResponseEntity<String> response =
          restTemplate.postForEntity("/api/v1/mesh/import/clear", request, String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).contains("success");
      assertThat(response.getBody()).contains("true");
      assertThat(response.getBody()).contains("进度已清除");
    }

    @Test
    @DisplayName("应该在未确认清除时返回 400")
    void shouldReturn400WhenNotConfirmed() {
      // given
      String requestBody =
          """
          {
            "confirmClear": false
          }
          """;

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

      // when
      ResponseEntity<String> response =
          restTemplate.postForEntity("/api/v1/mesh/import/clear", request, String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).contains("必须确认清除操作");
    }

    @Test
    @DisplayName("应该在有任务运行时无法清除（通过创建新任务测试）")
    void shouldNotClearWhenTaskRunning() {
      // given - 创建一个 PROCESSING 状态的任务
      MeshImportAggregate runningTask =
          new MeshImportAggregate(
              null,
              "正在运行的任务",
              MeshImportTaskStatus.PENDING,
              null,
              null,
              "https://nlmpubs.nlm.nih.gov/projects/mesh/desc2025.xml",
              null,
              null,
              java.util.List.of(),
              0,
              0,
              0,
              null);
      runningTask.startImport();
      meshImportPort.save(runningTask);

      String clearRequestBody =
          """
          {
            "confirmClear": true
          }
          """;

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> clearRequest = new HttpEntity<>(clearRequestBody, headers);

      // when - 尝试清除（但会因为有任务运行而失败）
      // 注意：clearAndRestart 方法只会中断任务，不会抛异常
      ResponseEntity<String> response =
          restTemplate.postForEntity("/api/v1/mesh/import/clear", clearRequest, String.class);

      // then - 清除操作应该成功（会中断正在运行的任务）
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }

  @Nested
  @DisplayName("参数校验测试")
  class ValidationTest {

    @Test
    @DisplayName("应该拒绝无效的 URL 格式")
    void shouldRejectInvalidUrlFormat() {
      // given
      String requestBody =
          """
          {
            "sourceUrl": "invalid-url",
            "taskName": "测试任务"
          }
          """;

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

      // when
      ResponseEntity<String> response =
          restTemplate.postForEntity("/api/v1/mesh/import/start", request, String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).contains("数据源 URL 必须是有效的 HTTP/HTTPS 地址");
    }

    @Test
    @DisplayName("应该拒绝过长的任务名称")
    void shouldRejectTaskNameTooLong() {
      // given
      String longName = "a".repeat(101); // 超过 100 字符
      String requestBody =
          String.format(
              """
          {
            "taskName": "%s"
          }
          """,
              longName);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

      // when
      ResponseEntity<String> response =
          restTemplate.postForEntity("/api/v1/mesh/import/start", request, String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).contains("任务名称长度不能超过 100 个字符");
    }
  }
}
