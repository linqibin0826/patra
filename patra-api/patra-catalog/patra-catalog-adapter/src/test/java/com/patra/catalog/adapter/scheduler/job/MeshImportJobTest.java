package com.patra.catalog.adapter.scheduler.job;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.patra.catalog.app.usecase.meshimport.MeshImportOrchestrator;
import com.patra.catalog.app.usecase.meshimport.dto.MeshImportResultDTO;
import com.xxl.job.core.context.XxlJobHelper;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/// MeSH 导入定时任务单元测试。
///
/// 测试策略：单元测试，Mock 业务层依赖和 Redisson 分布式锁
///
/// 测试覆盖：
///
/// - ✅ 获取分布式锁成功，调用 Orchestrator
///   - ✅ 获取分布式锁失败，记录日志并退出
///   - ✅ 执行过程中异常处理
///   - ✅ 正确释放分布式锁
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("MeSH 导入定时任务测试")
class MeshImportJobTest {

  @Mock private MeshImportOrchestrator meshImportOrchestrator;

  @Mock private RedissonClient redissonClient;

  @Mock private RLock lock;

  @InjectMocks private MeshImportJob meshImportJob;

  private MockedStatic<XxlJobHelper> xxlJobHelperMock;

  @BeforeEach
  void setUp() {
    // Mock XxlJobHelper 静态方法
    xxlJobHelperMock = mockStatic(XxlJobHelper.class);
    xxlJobHelperMock.when(XxlJobHelper::getJobId).thenReturn(1L);
    xxlJobHelperMock.when(XxlJobHelper::getJobParam).thenReturn("");
  }

  @AfterEach
  void tearDown() {
    xxlJobHelperMock.close();
  }

  @Nested
  @DisplayName("分布式锁获取成功场景")
  class LockAcquiredSuccessfully {

    @Test
    @DisplayName("应该成功获取锁并执行导入任务")
    void shouldExecuteImportWhenLockAcquiredSuccessfully() throws Exception {
      // given
      when(redissonClient.getLock("mesh:import:lock")).thenReturn(lock);
      when(lock.tryLock(0, 30, TimeUnit.MINUTES)).thenReturn(true);
      when(lock.isHeldByCurrentThread()).thenReturn(true);

      var resultDTO =
          MeshImportResultDTO.builder()
              .taskId("1234567890")
              .taskName("2025年MeSH数据导入")
              .status("PROCESSING")
              .startTime(Instant.now())
              .message("任务已启动")
              .build();

      when(meshImportOrchestrator.startImport(any())).thenReturn(resultDTO);

      // when
      meshImportJob.execute();

      // then
      verify(redissonClient).getLock("mesh:import:lock");
      verify(lock).tryLock(0, 30, TimeUnit.MINUTES);
      verify(meshImportOrchestrator).startImport(any());
      verify(lock).unlock();
    }

    @Test
    @DisplayName("应该在锁释放前记录成功日志")
    void shouldLogSuccessBeforeReleasingLock() throws Exception {
      // given
      when(redissonClient.getLock("mesh:import:lock")).thenReturn(lock);
      when(lock.tryLock(0, 30, TimeUnit.MINUTES)).thenReturn(true);
      when(lock.isHeldByCurrentThread()).thenReturn(true);

      var resultDTO =
          MeshImportResultDTO.builder()
              .taskId("1234567890")
              .taskName("测试任务名称")
              .status("SUCCESS")
              .message("任务已完成")
              .build();

      when(meshImportOrchestrator.startImport(any())).thenReturn(resultDTO);

      // when
      meshImportJob.execute();

      // then
      verify(lock).unlock();
      xxlJobHelperMock.verify(
          () -> XxlJobHelper.log(anyString()));
    }
  }

  @Nested
  @DisplayName("分布式锁获取失败场景")
  class LockAcquireFailed {

    @Test
    @DisplayName("应该在获取锁失败时跳过执行并记录日志")
    void shouldSkipExecutionWhenLockAcquireFailed() throws Exception {
      // given
      when(redissonClient.getLock("mesh:import:lock")).thenReturn(lock);
      when(lock.tryLock(0, 30, TimeUnit.MINUTES)).thenReturn(false);

      // when
      meshImportJob.execute();

      // then
      verify(lock).tryLock(0, 30, TimeUnit.MINUTES);
      verify(meshImportOrchestrator, never()).startImport(any());
      verify(lock, never()).unlock();
      xxlJobHelperMock.verify(() -> XxlJobHelper.log(anyString()));
    }

    @Test
    @DisplayName("应该在获取锁超时时正确处理")
    void shouldHandleLockTimeoutCorrectly() throws Exception {
      // given
      when(redissonClient.getLock("mesh:import:lock")).thenReturn(lock);
      when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

      // when
      meshImportJob.execute();

      // then
      verify(meshImportOrchestrator, never()).startImport(any());
      xxlJobHelperMock.verify(() -> XxlJobHelper.log(anyString()));
    }
  }

  @Nested
  @DisplayName("异常处理场景")
  class ExceptionHandling {

    @Test
    @DisplayName("应该在导入失败时释放锁并记录错误")
    void shouldReleaseLocakAndLogErrorWhenImportFailed() throws Exception {
      // given
      when(redissonClient.getLock("mesh:import:lock")).thenReturn(lock);
      when(lock.tryLock(0, 30, TimeUnit.MINUTES)).thenReturn(true);
      when(lock.isHeldByCurrentThread()).thenReturn(true);

      RuntimeException importException = new RuntimeException("MeSH 数据导入失败：网络连接超时");
      when(meshImportOrchestrator.startImport(any())).thenThrow(importException);

      // when
      assertThatThrownBy(() -> meshImportJob.execute())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("MeSH 数据导入失败：网络连接超时");

      // then
      verify(lock).unlock();
      xxlJobHelperMock.verify(() -> XxlJobHelper.log(anyString()), atLeastOnce());
    }

    @Test
    @DisplayName("应该处理 IllegalStateException（已有任务运行）")
    void shouldHandleIllegalStateExceptionGracefully() throws Exception {
      // given
      when(redissonClient.getLock("mesh:import:lock")).thenReturn(lock);
      when(lock.tryLock(0, 30, TimeUnit.MINUTES)).thenReturn(true);
      when(lock.isHeldByCurrentThread()).thenReturn(true);

      IllegalStateException stateException =
          new IllegalStateException("已有正在运行的 MeSH 导入任务，请等待其完成或手动中断");
      when(meshImportOrchestrator.startImport(any())).thenThrow(stateException);

      // when
      assertThatThrownBy(() -> meshImportJob.execute())
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("已有正在运行的 MeSH 导入任务，请等待其完成或手动中断");

      // then
      verify(lock).unlock();
      xxlJobHelperMock.verify(() -> XxlJobHelper.log(anyString()), atLeastOnce());
    }

    @Test
    @DisplayName("应该在获取锁抛出异常时记录错误")
    void shouldLogErrorWhenLockAcquisitionThrowsException() throws Exception {
      // given
      when(redissonClient.getLock("mesh:import:lock")).thenReturn(lock);
      when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
          .thenThrow(new InterruptedException("线程被中断"));

      // when
      assertThatThrownBy(() -> meshImportJob.execute()).isInstanceOf(RuntimeException.class);

      // then
      verify(meshImportOrchestrator, never()).startImport(any());
      verify(lock, never()).unlock();
      xxlJobHelperMock.verify(() -> XxlJobHelper.log(anyString(), any()));
    }
  }

  @Nested
  @DisplayName("锁释放机制验证")
  class LockReleaseTest {

    @Test
    @DisplayName("应该在 finally 块中释放锁，即使发生异常")
    void shouldReleaseLockInFinallyBlockEvenWhenExceptionOccurs() throws Exception {
      // given
      when(redissonClient.getLock("mesh:import:lock")).thenReturn(lock);
      when(lock.tryLock(0, 30, TimeUnit.MINUTES)).thenReturn(true);
      when(lock.isHeldByCurrentThread()).thenReturn(true);

      when(meshImportOrchestrator.startImport(any())).thenThrow(new RuntimeException("模拟导入异常"));

      // when
      assertThatThrownBy(() -> meshImportJob.execute()).isInstanceOf(RuntimeException.class);

      // then
      verify(lock).unlock();
    }

    @Test
    @DisplayName("应该在成功执行后释放锁")
    void shouldReleaseLockAfterSuccessfulExecution() throws Exception {
      // given
      when(redissonClient.getLock("mesh:import:lock")).thenReturn(lock);
      when(lock.tryLock(0, 30, TimeUnit.MINUTES)).thenReturn(true);
      when(lock.isHeldByCurrentThread()).thenReturn(true);

      var resultDTO =
          MeshImportResultDTO.builder()
              .taskId("1234567890")
              .taskName("测试任务名称")
              .status("SUCCESS")
              .message("任务已完成")
              .build();

      when(meshImportOrchestrator.startImport(any())).thenReturn(resultDTO);

      // when
      meshImportJob.execute();

      // then
      verify(lock, times(1)).unlock();
    }
  }

  @Nested
  @DisplayName("XXL-Job 参数处理")
  class JobParameterHandling {

    @Test
    @DisplayName("应该正确处理空参数")
    void shouldHandleEmptyParameterCorrectly() throws Exception {
      // given
      xxlJobHelperMock.when(XxlJobHelper::getJobParam).thenReturn("");

      when(redissonClient.getLock("mesh:import:lock")).thenReturn(lock);
      when(lock.tryLock(0, 30, TimeUnit.MINUTES)).thenReturn(true);
      when(lock.isHeldByCurrentThread()).thenReturn(true);

      var resultDTO = MeshImportResultDTO.builder().taskId("123").taskName("测试任务名称").status("SUCCESS").build();

      when(meshImportOrchestrator.startImport(any())).thenReturn(resultDTO);

      // when
      meshImportJob.execute();

      // then
      verify(meshImportOrchestrator).startImport(any());
    }

    @Test
    @DisplayName("应该正确处理带参数的调用")
    void shouldHandleParametersCorrectly() throws Exception {
      // given
      String jobParam = "{\"sourceUrl\":\"https://custom.url/desc.xml\"}";
      xxlJobHelperMock.when(XxlJobHelper::getJobParam).thenReturn(jobParam);

      when(redissonClient.getLock("mesh:import:lock")).thenReturn(lock);
      when(lock.tryLock(0, 30, TimeUnit.MINUTES)).thenReturn(true);
      when(lock.isHeldByCurrentThread()).thenReturn(true);

      var resultDTO = MeshImportResultDTO.builder().taskId("123").taskName("测试任务名称").status("SUCCESS").build();

      when(meshImportOrchestrator.startImport(any())).thenReturn(resultDTO);

      // when
      meshImportJob.execute();

      // then
      verify(meshImportOrchestrator).startImport(any());
      xxlJobHelperMock.verify(() -> XxlJobHelper.log(anyString()));
    }
  }
}
