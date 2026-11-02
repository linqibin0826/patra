package dev.patra.analytics.config;

import dev.patra.analytics.adapter.ingest.mq.EventIngestListener;
import dev.patra.analytics.infra.ingest.support.ClickHouseMigrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

/// 入库的启动顺序：后台执行 ClickHouse 建表迁移，失败按指数退避（1 秒起、上限 30 秒）一直重试，成功后再启动两个入库监听容器。
///
/// 不在启动时同步迁移：ClickHouse 连不上（宕机、mini 重启时 pipeline 栈比 apps 栈起得慢）时服务照常起来，采集照收，
/// 事件在 Kafka 里等，ClickHouse 恢复后入库自动接上（入库设计 §4.4）。放在组合根：它同时编排 infra 的迁移器与 adapter 的监听器。
@Slf4j
@Component
public class IngestStartup implements SmartLifecycle {

  /// 重试退避起点
  private static final long INITIAL_BACKOFF_MS = 1_000;

  /// 重试退避上限
  private static final long MAX_BACKOFF_MS = 30_000;

  private final ClickHouseMigrator migrator;
  private final KafkaListenerEndpointRegistry listeners;
  private final boolean ingestEnabled;
  private volatile boolean running;
  private volatile Thread worker;

  /// 创建启动编排。
  ///
  /// @param migrator ClickHouse 迁移器
  /// @param listeners 监听容器注册表
  /// @param ingestEnabled 是否启动入库消费者（测试里默认关，迁移照常执行）
  public IngestStartup(
      ClickHouseMigrator migrator,
      KafkaListenerEndpointRegistry listeners,
      @Value("${patra.analytics.ingest.enabled}") boolean ingestEnabled) {
    this.migrator = migrator;
    this.listeners = listeners;
    this.ingestEnabled = ingestEnabled;
  }

  /// 起一个后台线程迁移并启动消费者，不阻塞容器启动。
  @Override
  public void start() {
    running = true;
    worker = Thread.ofVirtual().name("clickhouse-migration").start(this::migrateThenStartListeners);
  }

  /// 停掉还在重试的后台线程（监听容器由注册表自己停）。
  @Override
  public void stop() {
    running = false;
    Thread current = worker;
    if (current != null) {
      current.interrupt();
    }
  }

  /// 是否已启动。
  ///
  /// @return 已启动为 `true`
  @Override
  public boolean isRunning() {
    return running;
  }

  /// 迁移成功前一直重试；成功后按开关启动两个监听容器。
  private void migrateThenStartListeners() {
    long backoff = INITIAL_BACKOFF_MS;
    while (running) {
      try {
        migrator.migrate();
        if (ingestEnabled) {
          listeners.getListenerContainer(EventIngestListener.RAW_LISTENER_ID).start();
          listeners.getListenerContainer(EventIngestListener.DLQ_LISTENER_ID).start();
          log.info("ClickHouse 迁移完成，入库消费者已启动");
        }
        return;
      } catch (RuntimeException e) {
        log.warn("ClickHouse 迁移失败，{} 毫秒后重试: {}", backoff, e.getMessage());
      }
      try {
        Thread.sleep(backoff);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
    }
  }
}
