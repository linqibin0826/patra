/**
 * Catalog 适配器层外部交互包。
 *
 * <p>Adapter 层负责处理外部交互，包括 REST API、消息监听、定时任务等，是系统的入口和出口。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>REST API：提供 HTTP 接口供客户端调用
 *   <li>定时任务：通过 XXL-Job 定期执行导入任务
 *   <li>消息监听：订阅 RocketMQ 消息触发业务流程（规划中）
 *   <li>请求验证：验证 HTTP 请求参数的合法性
 *   <li>异常转换：将领域异常转换为 HTTP 响应（委托给全局异常处理器）
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li><b>rest 包</b> - REST API 控制器
 *     <ul>
 *       <li>{@link com.patra.catalog.adapter.rest.MeshImportController} - MeSH 导入管理 API
 *         <ul>
 *           <li>POST /api/v1/mesh/import/start - 开始导入任务</li>
 *           <li>POST /api/v1/mesh/import/retry/{taskId} - 重试失败任务</li>
 *           <li>POST /api/v1/mesh/import/clear - 清除数据重新导入</li>
 *           <li>GET /api/v1/mesh/import/progress/{taskId} - 查询导入进度（规划中）</li>
 *           <li>GET /api/v1/mesh/import/tasks - 查询所有导入任务（规划中）</li>
 *         </ul>
 *       </li>
 *     </ul>
 *   </li>
 *   <li><b>scheduler.job 包</b> - XXL-Job 定时任务
 *     <ul>
 *       <li>{@link com.patra.catalog.adapter.scheduler.job.MeshImportJob} - MeSH 数据导入任务执行器
 *         <ul>
 *           <li>@XxlJob("meshImport")：XXL-Job 任务注解</li>
 *           <li>使用 Redisson 分布式锁避免并发导入</li>
 *           <li>调用 MeshImportOrchestrator 执行导入流程</li>
 *         </ul>
 *       </li>
 *     </ul>
 *   </li>
 *   <li><b>rocketmq 包</b> - RocketMQ 消息监听器（规划中）
 *     <ul>
 *       <li>TaskReadyMessageListener - 任务就绪消息监听器</li>
 *     </ul>
 *   </li>
 *   <li><b>rest.assembler 包</b> - Assembler 模式对象转换器
 *     <ul>
 *       <li>{@link com.patra.catalog.adapter.rest.assembler.StartImportAssembler} - 开始导入请求转换器
 *         <ul>
 *           <li>toCommand(StartImportRequest)：HTTP 请求 → 命令对象</li>
 *         </ul>
 *       </li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><b>单向依赖</b>：Adapter 层依赖 Application 层，不直接调用 Domain 层或 Infrastructure 层
 *   <li><b>薄 Controller</b>：Controller 只负责参数验证和调用 Orchestrator，不包含业务逻辑
 *   <li><b>异常委托</b>：Controller 不处理异常，委托给全局异常处理器（GlobalRestExceptionHandler）
 *   <li><b>Assembler 模式</b>：使用 Assembler 将 HTTP 请求转换为命令对象，避免 Controller 直接依赖 Request 对象
 *   <li><b>统一响应格式</b>：所有 API 使用统一的响应格式（通过 ResponseDTO 封装）
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例 1：REST API 控制器
 * @RestController
 * @RequestMapping("/api/v1/mesh/import")
 * @RequiredArgsConstructor
 * public class MeshImportController {
 *
 *     private final MeshImportOrchestrator meshImportOrchestrator;
 *     private final StartImportAssembler startImportAssembler;
 *
 *     @PostMapping("/start")
 *     public ResponseEntity<MeshImportResultDTO> startImport(
 *         @RequestBody @Valid StartImportRequest request
 *     ) {
 *         // 1. 转换请求对象为命令对象
 *         StartImportCommand command = startImportAssembler.toCommand(request);
 *
 *         // 2. 调用 Application 层编排器
 *         MeshImportResultDTO result = meshImportOrchestrator.startImport(command);
 *
 *         // 3. 返回响应（异常由全局异常处理器处理）
 *         return ResponseEntity.ok(result);
 *     }
 *
 *     @PostMapping("/retry/{taskId}")
 *     public ResponseEntity<MeshImportResultDTO> retryFailedTask(
 *         @PathVariable String taskId
 *     ) {
 *         MeshImportId importId = MeshImportId.of(Long.parseLong(taskId));
 *         MeshImportResultDTO result = meshImportOrchestrator.retryFailedTask(importId);
 *         return ResponseEntity.ok(result);
 *     }
 *
 *     @PostMapping("/clear")
 *     public ResponseEntity<Void> clearAndRestart() {
 *         meshImportOrchestrator.clearAndRestart();
 *         return ResponseEntity.ok().build();
 *     }
 * }
 *
 * // 示例 2：XXL-Job 定时任务
 * @Component
 * @RequiredArgsConstructor
 * @Slf4j
 * public class MeshImportJob {
 *
 *     private final MeshImportOrchestrator meshImportOrchestrator;
 *     private final RedissonClient redissonClient;
 *
 *     @XxlJob("meshImport")
 *     public void run() {
 *         RLock lock = redissonClient.getLock("mesh:import:lock");
 *
 *         // 尝试获取分布式锁（避免并发导入）
 *         if (!lock.tryLock()) {
 *             log.warn("MeSH 导入任务正在运行中，跳过本次执行");
 *             return;
 *         }
 *
 *         try {
 *             log.info("开始执行 MeSH 导入任务");
 *
 *             // 调用 Application 层编排器
 *             StartImportCommand command = new StartImportCommand(null, null);
 *             MeshImportResultDTO result = meshImportOrchestrator.startImport(command);
 *
 *             log.info("MeSH 导入任务执行完成：{}", result);
 *
 *         } catch (Exception ex) {
 *             log.error("MeSH 导入任务执行失败", ex);
 *             throw ex;
 *         } finally {
 *             lock.unlock();
 *         }
 *     }
 * }
 *
 * // 示例 3：Assembler 模式对象转换
 * @Component
 * public class StartImportAssembler {
 *
 *     public StartImportCommand toCommand(StartImportRequest request) {
 *         return new StartImportCommand(
 *             request.getSourceUrl(),
 *             request.getTaskName()
 *         );
 *     }
 * }
 *
 * // 示例 4：异常处理（委托给全局异常处理器）
 * // Controller 不需要处理异常，异常由 MeshImportErrorMappingContributor 映射为 HTTP 状态码
 * // 示例：
 * //   - IllegalStateException → 409 Conflict（业务状态冲突）
 * //   - IllegalArgumentException（"任务不存在"）→ 404 Not Found
 * //   - IllegalArgumentException（其他）→ 400 Bad Request
 * }</pre>
 *
 * @since 0.2.0
 * @author Patra Team
 */
package com.patra.catalog.adapter;
