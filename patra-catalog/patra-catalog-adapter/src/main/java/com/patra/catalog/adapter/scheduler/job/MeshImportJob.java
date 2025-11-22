package com.patra.catalog.adapter.scheduler.job;

import com.patra.catalog.app.usecase.meshimport.command.StartImportCommand;
import com.patra.catalog.app.usecase.meshimport.dto.MeshImportResultDTO;
import com.patra.catalog.app.usecase.meshimport.MeshImportOrchestrator;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/// MeSH 数据导入定时任务。
/// 
/// 通过 XXL-Job 调度，定期执行 MeSH 数据导入任务。使用 Redisson 分布式锁确保同一时刻只有一个实例执行导入。
/// 
/// **职责**：
/// 
/// - 暴露 XXL-Job 执行入口点（@XxlJob 注解）
///   - 获取 Redisson 分布式锁，避免并发导入
///   - 调用 {@link MeshImportOrchestrator} 执行导入逻辑
///   - 记录执行日志到 XXL-Job 控制台
/// 
/// **分布式锁策略**：
/// 
/// - 锁名称：`mesh:import:lock`
///   - 获取方式：非阻塞（tryLock）
///   - 锁超时：30 分钟（租约时间）
///   - 如果获取失败，记录日志并退出（不抛异常）
/// 
/// **调度策略**：
/// 
/// - 由 XXL-Job 调度中心配置（通常按需手动触发）
///   - 支持手动触发和失败重试
/// 
/// **异常处理**：
/// 
/// - 导入失败：记录错误日志，抛出异常（XXL-Job 会标记为失败）
///   - 锁获取失败：记录日志，正常退出（不视为任务失败）
///   - 确保 finally 块中释放锁
/// 
/// @author linqibin
/// @since 0.2.0
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshImportJob {

  /// 分布式锁名称
  private static final String LOCK_NAME = "mesh:import:lock";

  /// 锁租约时间（30 分钟）
  private static final long LOCK_LEASE_TIME = 30;

  /// 锁租约时间单位
  private static final TimeUnit LOCK_TIME_UNIT = TimeUnit.MINUTES;

  private final MeshImportOrchestrator meshImportOrchestrator;
  private final RedissonClient redissonClient;

  /// XXL-Job 执行入口点。
/// 
/// 任务名称：`meshImport`
/// 
/// 执行流程：
/// 
/// @throws RuntimeException 如果导入失败
  @XxlJob("meshImport")
  public void execute() {
    String jobParam = XxlJobHelper.getJobParam();
    log.debug("MeSH 导入任务已触发，jobId [{}]，参数：{}", XxlJobHelper.getJobId(), jobParam);
    XxlJobHelper.log("MeSH 导入任务已触发，jobId [{}]，参数：{}", XxlJobHelper.getJobId(), jobParam);

    RLock lock = redissonClient.getLock(LOCK_NAME);
    boolean lockAcquired = false;

    try {
      // 尝试获取分布式锁（非阻塞）
      lockAcquired = lock.tryLock(0, LOCK_LEASE_TIME, LOCK_TIME_UNIT);

      if (!lockAcquired) {
        log.warn("无法获取分布式锁，跳过本次执行（可能有其他实例正在运行）");
        XxlJobHelper.log("无法获取分布式锁，跳过本次执行（可能有其他实例正在运行）");
        return;
      }

      log.info("成功获取分布式锁，开始执行 MeSH 导入任务");
      XxlJobHelper.log("成功获取分布式锁，开始执行 MeSH 导入任务");

      // 调用 Orchestrator 执行导入
      StartImportCommand command = new StartImportCommand(null, null); // 使用默认配置
      MeshImportResultDTO result = meshImportOrchestrator.startImport(command);

      log.info(
          "MeSH 导入任务执行成功，任务 ID：{}，状态：{}，消息：{}",
          result.getTaskId(),
          result.getStatus(),
          result.getMessage());
      XxlJobHelper.log(
          "MeSH 导入任务执行成功，任务 ID：{}，状态：{}，消息：{}",
          result.getTaskId(),
          result.getStatus(),
          result.getMessage());

    } catch (IllegalStateException ex) {
      // 业务状态冲突（如已有任务运行）
      log.warn("MeSH 导入任务执行失败（业务状态冲突）：{}", ex.getMessage());
      XxlJobHelper.log("MeSH 导入任务执行失败（业务状态冲突）：{}", ex.getMessage());
      throw ex; // 重新抛出，让 XXL-Job 标记为失败

    } catch (RuntimeException ex) {
      // 其他运行时异常
      log.error("MeSH 导入任务执行失败：", ex);
      XxlJobHelper.log("MeSH 导入任务执行失败：{}", ex.getMessage());
      throw ex; // 重新抛出，让 XXL-Job 标记为失败

    } catch (InterruptedException ex) {
      // 锁获取被中断
      Thread.currentThread().interrupt(); // 恢复中断状态
      log.error("获取分布式锁时发生异常（线程被中断）：", ex);
      XxlJobHelper.log("获取分布式锁时发生异常（线程被中断）：{}", ex.getMessage());
      throw new RuntimeException("获取分布式锁时发生异常", ex);

    } finally {
      // 释放锁（如果获取成功）
      if (lockAcquired && lock.isHeldByCurrentThread()) {
        lock.unlock();
        log.debug("分布式锁已释放");
        XxlJobHelper.log("分布式锁已释放");
      }
    }
  }
}
