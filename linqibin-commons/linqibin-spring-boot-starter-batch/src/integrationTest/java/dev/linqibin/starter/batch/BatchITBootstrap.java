package dev.linqibin.starter.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试应用启动类
 *
 * <p>用于 @SpringBootTest 集成测试
 *
 * @author Patra Lin
 * @since 0.1.0
 */
@SpringBootApplication
public class BatchITBootstrap {

  /**
   * 启动测试应用
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    SpringApplication.run(BatchITBootstrap.class, args);
  }
}
