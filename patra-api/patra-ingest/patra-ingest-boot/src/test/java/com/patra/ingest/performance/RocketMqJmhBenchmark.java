package com.patra.ingest.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.common.message.Message;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * RocketMQ JMH 微基准测试。
 *
 * <p>使用 JMH (Java Microbenchmark Harness) 进行精确的性能测量。
 *
 * <p><strong>测试场景</strong>:
 *
 * <ul>
 *   <li>✅ 消息序列化开销
 *   <li>✅ RocketMQ 消息构建开销
 *   <li>✅ 发布器端到端性能
 * </ul>
 *
 * <p><strong>运行方式</strong>:
 *
 * <pre>
 * # 方式 1: 直接运行 main 方法
 * java -cp target/test-classes:target/classes com.patra.ingest.performance.RocketMqJmhBenchmark
 *
 * # 方式 2: 使用 Maven Exec 插件
 * mvn exec:java -Dexec.mainClass="com.patra.ingest.performance.RocketMqJmhBenchmark" \
 *   -Dexec.classpathScope=test
 *
 * # 方式 3: 编译为独立 JAR 并运行
 * mvn clean package
 * java -jar target/benchmarks.jar
 * </pre>
 *
 * <p><strong>JMH 参数说明</strong>:
 *
 * <ul>
 *   <li>Mode.Throughput: 测量吞吐量 (ops/sec)
 *   <li>Mode.AverageTime: 测量平均时间 (ms/op)
 *   <li>Warmup: 3 iterations, 1 second each (预热)
 *   <li>Measurement: 5 iterations, 1 second each (测量)
 *   <li>Fork: 1 (独立 JVM 进程)
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class RocketMqJmhBenchmark {

  private ObjectMapper objectMapper;
  private Map<String, Object> testPayload;

  /** JMH Setup: 初始化测试环境。 */
  @Setup
  public void setup() {
    objectMapper = new ObjectMapper();

    // 创建测试数据
    testPayload = new HashMap<>();
    testPayload.put("taskId", 12345L);
    testPayload.put("idempotentKey", "test-key");
    testPayload.put("timestamp", System.currentTimeMillis());
  }

  /** Benchmark 1: 测量 JSON 序列化开销。 */
  @Benchmark
  public String benchmarkJsonSerialization() throws Exception {
    return objectMapper.writeValueAsString(testPayload);
  }

  /** Benchmark 2: 测量 JSON 反序列化开销。 */
  @Benchmark
  public Map<String, Object> benchmarkJsonDeserialization() throws Exception {
    String json = "{\"taskId\": 12345, \"idempotentKey\": \"test-key\", \"timestamp\": 1234567890}";
    return objectMapper.readValue(json, Map.class);
  }

  /** Benchmark 3: 测量 RocketMQ Message 构建开销。 */
  @Benchmark
  public Message benchmarkRocketMqMessageBuilding() {
    Message message = new Message();
    message.setTopic("INGEST_TASK_READY");
    message.setTags("TaskReady");
    message.setKeys("benchmark-dedup-key");
    message.setBody("{\"taskId\": 12345}".getBytes());
    message.putUserProperty("channel", "TASK_READY");
    message.putUserProperty("opType", "TaskReady");
    return message;
  }

  // ==================== Helper Classes ====================

  /** JMH Main 方法: 运行基准测试。 */
  public static void main(String[] args) throws RunnerException {
    Options opt =
        new OptionsBuilder()
            .include(RocketMqJmhBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(3)
            .measurementIterations(5)
            .build();

    new Runner(opt).run();
  }
}
