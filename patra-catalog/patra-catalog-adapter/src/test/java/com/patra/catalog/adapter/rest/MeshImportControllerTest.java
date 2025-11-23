package com.patra.catalog.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.patra.catalog.adapter.config.TestConfiguration;
import com.patra.catalog.api.dto.MeshProgressDTO;
import com.patra.catalog.app.error.MeshImportErrorMappingContributor;
import com.patra.catalog.app.usecase.meshimport.MeshImportOrchestrator;
import com.patra.catalog.app.usecase.meshimport.MeshProgressQueryOrchestrator;
import com.patra.catalog.app.usecase.meshimport.dto.MeshImportResultDTO;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.starter.core.error.config.CoreErrorAutoConfiguration;
import com.patra.starter.web.error.config.WebErrorAutoConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/// MeSH 导入 Controller 切片测试。
///
/// 测试策略：使用 @WebMvcTest 切片测试，验证 HTTP 请求/响应、参数校验、全局异常处理
///
/// **异常处理验证**：验证 RFC 7807 ProblemDetail 响应格式（由 `patra-spring-boot-starter-web`
/// 自动处理）
///
/// 测试覆盖：
///
/// - ✅ POST /api/v1/mesh/import/start - 成功场景
///   - ✅ POST /api/v1/mesh/import/start - 已有任务运行（409）
///   - ✅ POST /api/v1/mesh/import/retry/{taskId} - 成功场景
///   - ✅ POST /api/v1/mesh/import/retry/{taskId} - 任务不存在（404）
///   - ✅ POST /api/v1/mesh/import/clear - 成功场景
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest(MeshImportController.class)
@ContextConfiguration(
    classes = TestConfiguration.class) // 提供 Spring Boot 配置（包含 @EnableAutoConfiguration）
@Import({
  MeshImportErrorMappingContributor.class, // 导入异常映射贡献者
  CoreErrorAutoConfiguration.class, // 导入核心错误处理配置
  WebErrorAutoConfiguration.class // 导入 Web 错误处理配置（包含 GlobalRestExceptionHandler）
})
@DisplayName("MeSH 导入 Controller 测试")
class MeshImportControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MeshImportOrchestrator meshImportOrchestrator;

  @MockitoBean private MeshProgressQueryOrchestrator meshProgressQueryOrchestrator;

  @Nested
  @DisplayName("POST /api/v1/mesh/import/start - 开始导入任务")
  class StartImportTest {

    @Test
    @DisplayName("应该成功创建导入任务并返回 200")
    void shouldReturn200WhenStartImportSuccessfully() throws Exception {
      // given
      var resultDTO =
          MeshImportResultDTO.builder()
              .taskId("1234567890")
              .taskName("MeSH 数据导入 - 2025-01-20")
              .status("PENDING")
              .startTime(Instant.parse("2025-01-20T10:00:00Z"))
              .message("任务已创建，等待执行")
              .build();

      when(meshImportOrchestrator.startImport()).thenReturn(resultDTO);

      // when & then
      mockMvc
          .perform(post("/api/v1/mesh/import/start"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.taskId").value("1234567890"))
          .andExpect(jsonPath("$.taskName").value("MeSH 数据导入 - 2025-01-20"))
          .andExpect(jsonPath("$.status").value("PENDING"))
          .andExpect(jsonPath("$.message").value("任务已创建，等待执行"));
    }

    @Test
    @DisplayName("应该在已有任务运行时返回 409 (RFC 7807 ProblemDetail)")
    void shouldReturn409WhenTaskAlreadyRunning() throws Exception {
      // given
      when(meshImportOrchestrator.startImport())
          .thenThrow(new IllegalStateException("已有正在运行的 MeSH 导入任务，请等待其完成或手动中断"));

      // when & then - MeshImportErrorMappingContributor 映射到 409
      mockMvc
          .perform(post("/api/v1/mesh/import/start"))
          .andExpect(status().isConflict())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.title").exists())
          .andExpect(jsonPath("$.status").value(409))
          .andExpect(jsonPath("$.detail").value("已有正在运行的 MeSH 导入任务，请等待其完成或手动中断"));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/mesh/import/retry/{taskId} - 重试失败任务")
  class RetryFailedTaskTest {

    @Test
    @DisplayName("应该成功重试失败任务并返回 200")
    void shouldReturn200WhenRetrySuccessfully() throws Exception {
      // given
      String taskId = "1234567890";

      var resultDTO =
          MeshImportResultDTO.builder()
              .taskId(taskId)
              .taskName("MeSH 数据导入 - 2025-01-20")
              .status("PROCESSING")
              .startTime(Instant.now())
              .message("任务正在重试")
              .build();

      when(meshImportOrchestrator.retryFailedTask(any(MeshImportId.class))).thenReturn(resultDTO);

      // when & then
      mockMvc
          .perform(post("/api/v1/mesh/import/retry/{taskId}", taskId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.taskId").value(taskId))
          .andExpect(jsonPath("$.status").value("PROCESSING"))
          .andExpect(jsonPath("$.message").value("任务正在重试"));
    }

    @Test
    @DisplayName("应该在任务不存在时返回 404 (RFC 7807 ProblemDetail)")
    void shouldReturn404WhenTaskNotFound() throws Exception {
      // given
      String taskId = "9999999999";

      when(meshImportOrchestrator.retryFailedTask(any(MeshImportId.class)))
          .thenThrow(new IllegalArgumentException("任务不存在：" + taskId));

      // when & then - MeshImportErrorMappingContributor 映射到 404
      mockMvc
          .perform(post("/api/v1/mesh/import/retry/{taskId}", taskId))
          .andExpect(status().isNotFound())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.detail").value("任务不存在：" + taskId));
    }

    @Test
    @DisplayName("应该在任务状态不允许重试时返回 409 (RFC 7807 ProblemDetail)")
    void shouldReturn409WhenTaskStateNotRetryable() throws Exception {
      // given
      String taskId = "1234567890";

      when(meshImportOrchestrator.retryFailedTask(any(MeshImportId.class)))
          .thenThrow(new IllegalStateException("任务状态不允许重试，当前状态：SUCCESS"));

      // when & then - MeshImportErrorMappingContributor 映射到 409
      mockMvc
          .perform(post("/api/v1/mesh/import/retry/{taskId}", taskId))
          .andExpect(status().isConflict())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.status").value(409))
          .andExpect(jsonPath("$.detail").value("任务状态不允许重试，当前状态：SUCCESS"));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/mesh/import/clear - 清除进度重新开始")
  class ClearAndRestartTest {

    @Test
    @DisplayName("应该成功清除进度并返回 200")
    void shouldReturn200WhenClearSuccessfully() throws Exception {
      // given
      var requestBody =
          """
          {
            "confirmClear": true
          }
          """;

      // when & then
      mockMvc
          .perform(
              post("/api/v1/mesh/import/clear")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.message").value("进度已清除，可以重新开始导入"));
    }

    @Test
    @DisplayName("应该在未确认清除时返回 400 (RFC 7807 ProblemDetail)")
    void shouldReturn400WhenNotConfirmed() throws Exception {
      // given
      var requestBody =
          """
          {
            "confirmClear": false
          }
          """;

      // when & then - MeshImportErrorMappingContributor 映射到 400
      mockMvc
          .perform(
              post("/api/v1/mesh/import/clear")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.detail").value("必须确认清除操作（confirmClear 必须为 true）"));
    }

    @Test
    @DisplayName("应该在缺少 confirmClear 字段时返回 422 (RFC 7807 ProblemDetail)")
    void shouldReturn422WhenMissingConfirmClear() throws Exception {
      // given
      var requestBody = "{}";

      // when & then - 参数校验失败，全局异常处理器返回 422 Unprocessable Entity
      mockMvc
          .perform(
              post("/api/v1/mesh/import/clear")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("应该在有任务正在运行时返回 409 (RFC 7807 ProblemDetail)")
    void shouldReturn409WhenTaskRunning() throws Exception {
      // given
      var requestBody =
          """
          {
            "confirmClear": true
          }
          """;

      // 修正 mock: clear 操作调用的是 clearAndRestart(),而不是 startImport()
      doThrow(new IllegalStateException("有任务正在运行，无法清除进度"))
          .when(meshImportOrchestrator)
          .clearAndRestart();

      // when & then - MeshImportErrorMappingContributor 映射到 409
      mockMvc
          .perform(
              post("/api/v1/mesh/import/clear")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isConflict())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.status").value(409))
          .andExpect(jsonPath("$.detail").value("有任务正在运行，无法清除进度"));
    }
  }

  @Nested
  @DisplayName("GET /api/v1/mesh/import/progress/{taskId} - 查询导入进度")
  class GetProgressTest {

    @Test
    @DisplayName("应该成功查询任务进度并返回 200")
    void shouldReturn200WhenQueryProgressSuccessfully() throws Exception {
      // given
      String taskId = "1234567890";

      var progressDTO =
          MeshProgressDTO.builder()
              .taskId(taskId)
              .taskName("MeSH 数据导入 - 2025-01-20")
              .status("processing")
              .totalRecords(30000)
              .processedRecords(15000)
              .overallProgress(50.0)
              .processSpeed(500.0)
              .estimatedRemainingSeconds(300L)
              .startTime(Instant.parse("2025-01-20T10:00:00Z"))
              .endTime(null)
              .elapsedSeconds(300L)
              .tableProgress(java.util.List.of())
              .failedBatches(java.util.List.of())
              .build();

      when(meshProgressQueryOrchestrator.queryProgress(any(MeshImportId.class)))
          .thenReturn(progressDTO);

      // when & then
      mockMvc
          .perform(get("/api/v1/mesh/import/progress/{taskId}", taskId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.taskId").value(taskId))
          .andExpect(jsonPath("$.status").value("processing"))
          .andExpect(jsonPath("$.totalRecords").value(30000))
          .andExpect(jsonPath("$.processedRecords").value(15000))
          .andExpect(jsonPath("$.overallProgress").value(50.0))
          .andExpect(jsonPath("$.processSpeed").value(500.0))
          .andExpect(jsonPath("$.estimatedRemainingSeconds").value(300));
    }

    @Test
    @DisplayName("应该在任务不存在时返回 404 (RFC 7807 ProblemDetail)")
    void shouldReturn404WhenTaskNotFound() throws Exception {
      // given
      String taskId = "9999999999";

      when(meshProgressQueryOrchestrator.queryProgress(any(MeshImportId.class)))
          .thenThrow(new IllegalArgumentException("任务不存在: " + taskId));

      // when & then - MeshImportErrorMappingContributor 映射到 404
      mockMvc
          .perform(get("/api/v1/mesh/import/progress/{taskId}", taskId))
          .andExpect(status().isNotFound())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.detail").value("任务不存在: " + taskId));
    }
  }
}
