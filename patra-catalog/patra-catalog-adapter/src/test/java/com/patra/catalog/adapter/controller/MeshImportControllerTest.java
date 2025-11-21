package com.patra.catalog.adapter.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.patra.catalog.api.dto.MeshImportResultDTO;
import com.patra.catalog.app.usecase.meshimport.MeshImportOrchestrator;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MeSH 导入 Controller 切片测试。
 *
 * <p>测试策略：使用 @WebMvcTest 切片测试，验证 HTTP 请求/响应、参数校验、异常转换
 *
 * <p>测试覆盖：
 *
 * <ul>
 *   <li>✅ POST /api/v1/mesh/import/start - 成功场景
 *   <li>✅ POST /api/v1/mesh/import/start - 参数校验失败
 *   <li>✅ POST /api/v1/mesh/import/start - 已有任务运行（409）
 *   <li>✅ POST /api/v1/mesh/import/retry/{taskId} - 成功场景
 *   <li>✅ POST /api/v1/mesh/import/retry/{taskId} - 任务不存在（404）
 *   <li>✅ POST /api/v1/mesh/import/clear - 成功场景
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@WebMvcTest(MeshImportController.class)
@DisplayName("MeSH 导入 Controller 测试")
class MeshImportControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private MeshImportOrchestrator meshImportOrchestrator;

  @Nested
  @DisplayName("POST /api/v1/mesh/import/start - 开始导入任务")
  class StartImportTest {

    @Test
    @DisplayName("应该成功创建导入任务并返回 200")
    void shouldReturn200WhenStartImportSuccessfully() throws Exception {
      // given
      var requestBody =
          """
          {
            "sourceUrl": "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
            "taskName": "2025年MeSH数据首次导入"
          }
          """;

      var resultDTO =
          MeshImportResultDTO.builder()
              .taskId("1234567890")
              .taskName("2025年MeSH数据首次导入")
              .status("PENDING")
              .startTime(Instant.parse("2025-01-20T10:00:00Z"))
              .message("任务已创建，等待执行")
              .build();

      when(meshImportOrchestrator.startImport(any())).thenReturn(resultDTO);

      // when & then
      mockMvc
          .perform(
              post("/api/v1/mesh/import/start")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.taskId").value("1234567890"))
          .andExpect(jsonPath("$.taskName").value("2025年MeSH数据首次导入"))
          .andExpect(jsonPath("$.status").value("PENDING"))
          .andExpect(jsonPath("$.message").value("任务已创建，等待执行"));
    }

    @Test
    @DisplayName("应该支持空请求体（使用默认配置）")
    void shouldAcceptEmptyRequestBodyWithDefaults() throws Exception {
      // given
      var requestBody = "{}";

      var resultDTO =
          MeshImportResultDTO.builder()
              .taskId("1234567890")
              .taskName("2025年MeSH数据导入")
              .status("PENDING")
              .startTime(Instant.now())
              .message("任务已创建")
              .build();

      when(meshImportOrchestrator.startImport(any())).thenReturn(resultDTO);

      // when & then
      mockMvc
          .perform(
              post("/api/v1/mesh/import/start")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.taskId").exists());
    }

    @Test
    @DisplayName("应该拒绝无效的 URL 格式并返回 400")
    void shouldReturn400WhenInvalidUrlFormat() throws Exception {
      // given
      var requestBody =
          """
          {
            "sourceUrl": "invalid-url",
            "taskName": "测试任务"
          }
          """;

      // when & then
      mockMvc
          .perform(
              post("/api/v1/mesh/import/start")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("数据源 URL 必须是有效的 HTTP/HTTPS 地址"));
    }

    @Test
    @DisplayName("应该拒绝过长的任务名称并返回 400")
    void shouldReturn400WhenTaskNameTooLong() throws Exception {
      // given
      var longName = "a".repeat(101); // 超过 100 字符
      var requestBody =
          String.format(
              """
          {
            "taskName": "%s"
          }
          """,
              longName);

      // when & then
      mockMvc
          .perform(
              post("/api/v1/mesh/import/start")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("任务名称长度不能超过 100 个字符"));
    }

    @Test
    @DisplayName("应该在已有任务运行时返回 409")
    void shouldReturn409WhenTaskAlreadyRunning() throws Exception {
      // given
      var requestBody = "{}";

      when(meshImportOrchestrator.startImport(any()))
          .thenThrow(new IllegalStateException("已有正在运行的 MeSH 导入任务，请等待其完成或手动中断"));

      // when & then
      mockMvc
          .perform(
              post("/api/v1/mesh/import/start")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("TASK_ALREADY_RUNNING"))
          .andExpect(jsonPath("$.message").value("已有正在运行的 MeSH 导入任务，请等待其完成或手动中断"));
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
              .taskName("2025年MeSH数据导入")
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
    @DisplayName("应该在任务不存在时返回 404")
    void shouldReturn404WhenTaskNotFound() throws Exception {
      // given
      String taskId = "9999999999";

      when(meshImportOrchestrator.retryFailedTask(any(MeshImportId.class)))
          .thenThrow(new IllegalArgumentException("任务不存在：" + taskId));

      // when & then
      mockMvc
          .perform(post("/api/v1/mesh/import/retry/{taskId}", taskId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
          .andExpect(jsonPath("$.message").value("任务不存在：" + taskId));
    }

    @Test
    @DisplayName("应该在任务状态不允许重试时返回 400")
    void shouldReturn400WhenTaskStateNotRetryable() throws Exception {
      // given
      String taskId = "1234567890";

      when(meshImportOrchestrator.retryFailedTask(any(MeshImportId.class)))
          .thenThrow(new IllegalStateException("任务状态不允许重试，当前状态：SUCCESS"));

      // when & then
      mockMvc
          .perform(post("/api/v1/mesh/import/retry/{taskId}", taskId))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_TASK_STATE"))
          .andExpect(jsonPath("$.message").value("任务状态不允许重试，当前状态：SUCCESS"));
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
    @DisplayName("应该在未确认清除时返回 400")
    void shouldReturn400WhenNotConfirmed() throws Exception {
      // given
      var requestBody =
          """
          {
            "confirmClear": false
          }
          """;

      // when & then
      mockMvc
          .perform(
              post("/api/v1/mesh/import/clear")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("必须确认清除操作（confirmClear 必须为 true）"));
    }

    @Test
    @DisplayName("应该在缺少 confirmClear 字段时返回 400")
    void shouldReturn400WhenMissingConfirmClear() throws Exception {
      // given
      var requestBody = "{}";

      // when & then
      mockMvc
          .perform(
              post("/api/v1/mesh/import/clear")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("应该在有任务正在运行时返回 409")
    void shouldReturn409WhenTaskRunning() throws Exception {
      // given
      var requestBody =
          """
          {
            "confirmClear": true
          }
          """;

      when(meshImportOrchestrator.startImport(any()))
          .thenThrow(new IllegalStateException("有任务正在运行，无法清除进度"));

      // when & then
      mockMvc
          .perform(
              post("/api/v1/mesh/import/clear")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("TASK_RUNNING"))
          .andExpect(jsonPath("$.message").value("有任务正在运行，无法清除进度"));
    }
  }
}
