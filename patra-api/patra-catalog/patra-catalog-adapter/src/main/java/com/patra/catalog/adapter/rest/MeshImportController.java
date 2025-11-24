package com.patra.catalog.adapter.rest;

import com.patra.catalog.api.dto.MeshProgressDTO;
import com.patra.catalog.app.usecase.meshimport.MeshImportOrchestrator;
import com.patra.catalog.app.usecase.meshimport.MeshProgressQueryOrchestrator;
import com.patra.catalog.app.usecase.meshimport.dto.MeshImportResultDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/// MeSH 导入管理 REST API 控制器。
///
/// 提供 MeSH 数据导入的 HTTP 接口，包括：
///
/// - POST /api/v1/mesh/import/start - 开始导入任务
///   - POST /api/v1/mesh/import/retry/{taskId} - 重试失败任务
///   - POST /api/v1/mesh/import/clear - 清除进度重新开始
///   - GET /api/v1/mesh/import/progress/{taskId} - 查询导入进度（User Story 2）
///
/// **职责**：
///
/// - 接收 HTTP 请求并验证格式（{@link jakarta.validation.Valid}）
///   - 调用 {@link MeshImportOrchestrator} 执行业务逻辑
///   - 异常由全局 {@link com.patra.starter.web.error.handler.GlobalRestExceptionHandler} 处理
///
/// **异常处理**（由 {@link com.patra.catalog.app.error.MeshImportErrorMappingContributor} 映射）：
///
/// - {@link IllegalStateException} → 409 Conflict（业务状态冲突，如已有任务运行）
///   - {@link IllegalArgumentException}（任务不存在） → 404 Not Found
///   - {@link IllegalArgumentException}（参数错误） → 400 Bad Request
///   - 参数校验失败（{@link jakarta.validation.ConstraintViolationException}） → 400 Bad Request
///
/// **响应格式**：符合 RFC 7807 ProblemDetail 标准（由 patra-spring-boot-starter-web 自动处理）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RestController
@RequestMapping("/api/v1/mesh/import")
@RequiredArgsConstructor
@Validated
public class MeshImportController {

  private final MeshImportOrchestrator meshImportOrchestrator;
  private final MeshProgressQueryOrchestrator meshProgressQueryOrchestrator;

  /// 开始导入任务。
  ///
  /// 接口定义：POST /api/v1/mesh/import/start
  ///
  /// 流程：{@link MeshImportOrchestrator}
  ///
  /// 使用配置文件中预定义的数据源 URL 和任务名称启动导入任务。
  ///
  /// @return 导入任务结果
  /// @throws IllegalStateException 如果已有任务正在运行（返回 409 Conflict）
  @PostMapping("/start")
  public MeshImportResultDTO startImport() {
    log.info("收到 MeSH 导入请求，使用配置文件默认值");

    MeshImportResultDTO result = meshImportOrchestrator.startImport();

    log.info("MeSH 导入任务已创建，任务 ID：{}", result.getTaskId());

    return result;
  }

  /// 重试失败任务。
  ///
  /// 接口定义：POST /api/v1/mesh/import/retry/{taskId}
  ///
  /// @param taskId 任务 ID
  /// @return 导入任务结果
  @PostMapping("/retry/{taskId}")
  public MeshImportResultDTO retryFailedTask(@PathVariable @NotNull Long taskId) {
    log.info("收到重试失败任务请求，任务 ID：{}", taskId);

    MeshImportResultDTO result = meshImportOrchestrator.retryFailedTask(taskId);

    log.info("MeSH 导入任务重试已启动，任务 ID：{}", taskId);

    return result;
  }

  /// 清除进度重新开始。
  ///
  /// 接口定义：POST /api/v1/mesh/import/clear
  ///
  /// @param request 清除请求（必须确认清除）
  /// @return 操作结果
  /// @throws IllegalStateException 如果有任务正在运行（返回 409 Conflict）
  /// @throws IllegalArgumentException 如果未确认清除（返回 400 Bad Request）
  @PostMapping("/clear")
  public ClearImportResponse clearAndRestart(@Valid @RequestBody ClearImportRequest request) {
    log.info("收到清除进度请求，确认清除：{}", request.confirmClear());

    // 验证确认标志
    if (!request.confirmClear()) {
      throw new IllegalArgumentException("必须确认清除操作（confirmClear 必须为 true）");
    }

    meshImportOrchestrator.clearAndRestart();

    log.info("MeSH 导入进度已清除");

    return new ClearImportResponse(true, "进度已清除，可以重新开始导入");
  }

  /// 查询导入进度（User Story 2 - 实时监控导入进度）。
  ///
  /// 接口定义：GET /api/v1/mesh/import/progress/{taskId}
  ///
  /// 返回详细的导入进度信息，包括：
  ///
  /// - 整体进度百分比（overallProgress）
  ///   - 各表进度详情（tableProgress）
  ///   - 失败批次列表（failedBatches）
  ///   - 处理速度（processSpeed - 记录/秒）
  ///   - 预计剩余时间（estimatedRemainingSeconds）
  ///
  /// @param taskId 任务 ID
  /// @return 进度详情 DTO
  /// @throws IllegalArgumentException 如果任务不存在（返回 404 Not Found）
  /// @since 0.1.0
  @GetMapping("/progress/{taskId}")
  public MeshProgressDTO getProgress(@PathVariable @NotNull Long taskId) {
    log.info("收到查询导入进度请求，任务 ID：{}", taskId);

    MeshProgressDTO progress = meshProgressQueryOrchestrator.queryProgress(taskId);

    log.debug(
        "查询进度成功，任务 ID：{}，整体进度：{}%，处理速度：{} 记录/秒",
        taskId, progress.overallProgress(), progress.processSpeed());

    return progress;
  }

  /// 清除导入请求。
  ///
  /// @param confirmClear 确认清除标志（必须为 true）
  public record ClearImportRequest(
      @NotNull(message = "confirmClear 字段不能为空") Boolean confirmClear) {}

  /// 清除导入响应。
  ///
  /// @param success 操作是否成功
  /// @param message 操作结果消息
  public record ClearImportResponse(boolean success, String message) {}
}
