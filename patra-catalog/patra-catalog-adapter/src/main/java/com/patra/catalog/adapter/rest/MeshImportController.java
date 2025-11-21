package com.patra.catalog.adapter.rest;

import com.patra.catalog.api.command.StartImportCommand;
import com.patra.catalog.api.dto.MeshImportResultDTO;
import com.patra.catalog.app.usecase.meshimport.MeshImportOrchestrator;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MeSH 导入管理 REST API 控制器。
 *
 * <p>提供 MeSH 数据导入的 HTTP 接口，包括：
 *
 * <ul>
 *   <li>POST /api/v1/mesh/import/start - 开始导入任务
 *   <li>POST /api/v1/mesh/import/retry/{taskId} - 重试失败任务
 *   <li>POST /api/v1/mesh/import/clear - 清除进度重新开始
 * </ul>
 *
 * <p><b>职责</b>：
 *
 * <ul>
 *   <li>接收 HTTP 请求并转换为 Command
 *   <li>调用 {@link MeshImportOrchestrator} 执行业务逻辑
 *   <li>异常由全局 {@link com.patra.starter.web.error.handler.GlobalRestExceptionHandler} 处理
 * </ul>
 *
 * <p><b>异常处理</b>（由 {@link com.patra.catalog.app.error.MeshImportErrorMappingContributor} 映射）：
 *
 * <ul>
 *   <li>{@link IllegalStateException} → 409 Conflict（业务状态冲突，如已有任务运行）
 *   <li>{@link IllegalArgumentException}（任务不存在） → 404 Not Found
 *   <li>{@link IllegalArgumentException}（参数错误） → 400 Bad Request
 *   <li>参数校验失败（{@link jakarta.validation.ConstraintViolationException}） → 400 Bad Request
 * </ul>
 *
 * <p><b>响应格式</b>：符合 RFC 7807 ProblemDetail 标准（由 patra-spring-boot-starter-web 自动处理）
 *
 * @author linqibin
 * @since 0.2.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mesh/import")
@RequiredArgsConstructor
@Validated
public class MeshImportController {

  private final MeshImportOrchestrator meshImportOrchestrator;

  /**
   * 开始导入任务。
   *
   * <p>接口定义：POST /api/v1/mesh/import/start
   *
   * @param command 启动命令（可选参数，空请求体时使用默认配置）
   * @return 导入任务结果
   * @throws IllegalStateException 如果已有任务正在运行（返回 409 Conflict）
   */
  @PostMapping("/start")
  public ResponseEntity<MeshImportResultDTO> startImport(
      @Valid @RequestBody(required = false) StartImportCommand command) {
    log.info("收到 MeSH 导入请求，命令：{}", command);

    // 如果请求体为空，使用默认命令
    StartImportCommand actualCommand =
        (command != null) ? command : new StartImportCommand(null, null);

    MeshImportResultDTO result = meshImportOrchestrator.startImport(actualCommand);

    log.info("MeSH 导入任务已创建，任务 ID：{}", result.getTaskId());

    return ResponseEntity.ok(result);
  }

  /**
   * 重试失败任务。
   *
   * <p>接口定义：POST /api/v1/mesh/import/retry/{taskId}
   *
   * @param taskId 任务 ID
   * @return 导入任务结果
   * @throws IllegalArgumentException 如果任务不存在（返回 404 Not Found）
   * @throws IllegalStateException 如果任务状态不允许重试（返回 409 Conflict）
   */
  @PostMapping("/retry/{taskId}")
  public ResponseEntity<MeshImportResultDTO> retryFailedTask(
      @PathVariable @NotNull String taskId) {
    log.info("收到重试失败任务请求，任务 ID：{}", taskId);

    MeshImportId importId = new MeshImportId(Long.parseLong(taskId));
    MeshImportResultDTO result = meshImportOrchestrator.retryFailedTask(importId);

    log.info("MeSH 导入任务重试已启动，任务 ID：{}", taskId);

    return ResponseEntity.ok(result);
  }

  /**
   * 清除进度重新开始。
   *
   * <p>接口定义：POST /api/v1/mesh/import/clear
   *
   * @param request 清除请求（必须确认清除）
   * @return 操作结果
   * @throws IllegalStateException 如果有任务正在运行（返回 409 Conflict）
   * @throws IllegalArgumentException 如果未确认清除（返回 400 Bad Request）
   */
  @PostMapping("/clear")
  public ResponseEntity<Map<String, Object>> clearAndRestart(
      @Valid @RequestBody ClearImportRequest request) {
    log.info("收到清除进度请求，确认清除：{}", request.confirmClear());

    // 验证确认标志
    if (!request.confirmClear()) {
      throw new IllegalArgumentException("必须确认清除操作（confirmClear 必须为 true）");
    }

    meshImportOrchestrator.clearAndRestart();

    log.info("MeSH 导入进度已清除");

    return ResponseEntity.ok(
        Map.of(
            "success", true,
            "message", "进度已清除，可以重新开始导入"));
  }

  /**
   * 清除导入请求。
   *
   * @param confirmClear 确认清除标志（必须为 true）
   */
  public record ClearImportRequest(
      @NotNull(message = "confirmClear 字段不能为空") Boolean confirmClear) {}
}
