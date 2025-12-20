package com.patra.starter.jpa.id;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.concurrent.ThreadLocalRandom;

/// 雪花算法 ID 生成器。
///
/// **算法结构**（64 位）：
///
/// ```
/// | 1 bit | 41 bits    | 5 bits      | 5 bits    | 12 bits  |
/// | 符号  | 时间戳     | 数据中心 ID | 机器 ID   | 序列号   |
/// ```
///
/// **特性**：
///
/// - **时间戳**：毫秒级精度，支持约 69 年（从自定义纪元开始）
/// - **机器标识**：自动从 MAC 地址派生，支持 1024 个节点
/// - **序列号**：每毫秒 4096 个 ID，单节点 QPS 约 400 万
/// - **线程安全**：通过 synchronized 保证并发安全
///
/// **使用方式**：
///
/// 在应用层预分配 ID，避免依赖数据库自增，保证 JPA 批量插入性能。
///
/// 使用示例：
///
/// ```java
/// Long id = SnowflakeIdGenerator.getId();
/// entity.setId(id);
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class SnowflakeIdGenerator {

  /// 起始时间戳（2024-01-01 00:00:00 UTC）。
  private static final long EPOCH = 1704067200000L;

  /// 机器 ID 占用位数。
  private static final long WORKER_ID_BITS = 5L;

  /// 数据中心 ID 占用位数。
  private static final long DATACENTER_ID_BITS = 5L;

  /// 序列号占用位数。
  private static final long SEQUENCE_BITS = 12L;

  /// 机器 ID 最大值（31）。
  private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

  /// 数据中心 ID 最大值（31）。
  private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

  /// 序列号最大值（4095）。
  private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

  /// 机器 ID 左移位数。
  private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

  /// 数据中心 ID 左移位数。
  private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

  /// 时间戳左移位数。
  private static final long TIMESTAMP_LEFT_SHIFT =
      SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

  /// 单例实例。
  private static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator();

  /// 数据中心 ID。
  private final long datacenterId;

  /// 机器 ID。
  private final long workerId;

  /// 当前序列号。
  private long sequence = 0L;

  /// 上次生成 ID 的时间戳。
  private long lastTimestamp = -1L;

  /// 私有构造函数，自动派生机器标识。
  private SnowflakeIdGenerator() {
    long[] ids = deriveIdsFromMac();
    this.datacenterId = ids[0];
    this.workerId = ids[1];
  }

  /// 获取下一个雪花 ID。
  ///
  /// **线程安全**：此方法通过 synchronized 保证并发安全。
  ///
  /// @return 全局唯一的 64 位 ID
  /// @throws IllegalStateException 如果系统时钟回退
  public static long getId() {
    return INSTANCE.nextId();
  }

  /// 获取下一个雪花 ID（字符串形式）。
  ///
  /// @return 全局唯一的 ID 字符串
  public static String getIdStr() {
    return String.valueOf(getId());
  }

  /// 生成下一个 ID（内部方法）。
  private synchronized long nextId() {
    long timestamp = currentTimeMillis();

    // 时钟回退检查
    if (timestamp < lastTimestamp) {
      long offset = lastTimestamp - timestamp;
      if (offset <= 5) {
        // 等待时钟追上
        try {
          wait(offset << 1);
          timestamp = currentTimeMillis();
          if (timestamp < lastTimestamp) {
            throw new IllegalStateException(
                "Clock moved backwards. Refusing to generate id for " + offset + " milliseconds");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while waiting for clock to catch up", e);
        }
      } else {
        throw new IllegalStateException(
            "Clock moved backwards. Refusing to generate id for " + offset + " milliseconds");
      }
    }

    // 同一毫秒内，序列号递增
    if (lastTimestamp == timestamp) {
      sequence = (sequence + 1) & SEQUENCE_MASK;
      // 序列号溢出，等待下一毫秒
      if (sequence == 0) {
        timestamp = waitNextMillis(lastTimestamp);
      }
    } else {
      // 新的毫秒，序列号重置（使用随机初始值避免低位偏斜）
      sequence = ThreadLocalRandom.current().nextLong(0, 3);
    }

    lastTimestamp = timestamp;

    return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
        | (datacenterId << DATACENTER_ID_SHIFT)
        | (workerId << WORKER_ID_SHIFT)
        | sequence;
  }

  /// 等待下一毫秒。
  private long waitNextMillis(long lastTimestamp) {
    long timestamp = currentTimeMillis();
    while (timestamp <= lastTimestamp) {
      timestamp = currentTimeMillis();
    }
    return timestamp;
  }

  /// 获取当前时间戳。
  private long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  /// 从 MAC 地址派生数据中心 ID 和机器 ID。
  ///
  /// 如果无法获取 MAC 地址，使用随机值。
  private static long[] deriveIdsFromMac() {
    try {
      InetAddress ip = InetAddress.getLocalHost();
      NetworkInterface network = NetworkInterface.getByInetAddress(ip);

      if (network != null) {
        byte[] mac = network.getHardwareAddress();
        if (mac != null && mac.length >= 6) {
          // 使用 MAC 地址的后两个字节派生 ID
          long datacenterId = ((mac[mac.length - 2] & 0xFF) % (MAX_DATACENTER_ID + 1));
          long workerId = ((mac[mac.length - 1] & 0xFF) % (MAX_WORKER_ID + 1));
          return new long[] {datacenterId, workerId};
        }
      }
    } catch (Exception ignored) {
      // 无法获取 MAC 地址，使用随机值
    }

    // 回退：使用随机值
    return new long[] {
      ThreadLocalRandom.current().nextLong(0, MAX_DATACENTER_ID + 1),
      ThreadLocalRandom.current().nextLong(0, MAX_WORKER_ID + 1)
    };
  }
}
