## 模块：patra-parent (Maven 父 POM)

统一版本 & 构建治理中心，所有子模块继承，确保：

1. 版本收敛：Spring Boot / Spring Cloud / Cloud Alibaba / MyBatis-Plus / RocketMQ / Hutool / XXL-Job / MapStruct / Lombok
2. 统一 Java 版本（release=21）与编码（UTF-8）
3. 注解处理器集中声明（Lombok / MapStruct / lombok-mapstruct-binding）
4. 依赖版本只在此定义（子模块禁止再声明 version，防止漂移）
5. 提供内部模块 BOM（common / starters / kernel 等）

---

## 1. 结构概览

`pom.xml` 仅包含 `<dependencyManagement>` + 基础依赖（Lombok / JUnit）。真正业务依赖由子模块自行按需引入。

---

## 2. 关键属性

| 属性 | 说明 |
|------|------|
| java.version | 统一 JDK 版本（与 maven.compiler.release 对齐） |
| project.version | 多模块内部版本号传递（SNAPSHOT） |
| spring-boot.version | Spring Boot 主版本，驱动依赖集一致性 |
| spring-cloud.version | Spring Cloud BOM 版本 |
| spring-cloud-alibaba.version | Nacos / RocketMQ / Sentinel 等套件版本 |
| mybatis-plus.version | ORM 插件版本 |
| rocketmq.spring.version | 官方 Spring Starter 版本 |
| hutool.version | Hutool 工具库基础模块版本 |
| xxl-job.version | 调度组件版本 |
| mapstruct / lombok.* | 映射与样板代码生成工具链 |

---

## 3. 依赖管理策略

| 分类 | 策略 |
|------|------|
| 第三方 BOM | Import 官方 BOM（Boot / Cloud / Cloud Alibaba）避免人工逐个版本号维护 |
| 内部模块 | 通过 `<dependencyManagement>` 锁定同一 `${project.version}` |
| 可选依赖 | 仅 MapStruct 标记 optional，防止传递；Lombok 不设 optional 保证编译期生效 |
| 测试依赖 | 只提供 JUnit Jupiter 基础，其他（Mockito / AssertJ）子模块按需添加 |

---

## 4. 子模块使用规范

子模块 `pom.xml`：
```xml
<parent>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-parent</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <relativePath>../patra-parent/pom.xml</relativePath>
</parent>

<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <!-- 不要写 <version> -->
</dependencies>
```

禁止：
1. 再次声明已在 parent 管理的依赖 `<version>`
2. 在业务模块直接变更 JDK 版本
3. 引入不受控 BOM（导致覆盖 Boot 管理）

---

## 5. 注解处理器统一

避免各模块重复配置：`maven-compiler-plugin` 中集中声明 Lombok / MapStruct Processor；子模块无需再写 `<annotationProcessorPaths>`。

---

## 6. 版本升级流程（建议）

| 步骤 | 内容 |
|------|------|
| 1 | 新建分支 `chore/upgrade-spring-boot-3.2.5` |
| 2 | 调整 parent 中相关属性（Boot + Cloud 兼容矩阵核对） |
| 3 | 全仓 `mvn -T1C -DskipTests compile` 验证编译 |
| 4 | 跑关键模块测试（core/web/feign） |
| 5 | 更新 CHANGELOG/升级说明（不兼容点） |
| 6 | Merge + 打 TAG（若非 SNAPSHOT 发布） |

---

## 7. 常见问题 (FAQ)

| 问题 | 处理建议 |
|------|----------|
| 模块引用出现“版本冲突” | 检查是否直接写了 `<version>` 覆盖 BOM；执行 `mvn dependency:tree` 分析 |
| Lombok 失效 | IDEA 需启用注解处理；确认未在子模块引入冲突旧版 Lombok |
| MapStruct 生成失败 | 检查 processor 版本与 mapstruct.version 是否一致 |
| 需要新三方库 | 在业务模块直接加入（若需要跨模块统一再上提到 parent） |

---

## 8. 后续 Roadmap

| 优先级 | 项目 | 描述 |
|--------|------|------|
| High | 统一 Spotless / Checkstyle | 引入统一代码格式化 & 静态检查配置 |
| High | Enforcer 规则强化 | 禁止 SNAPSHOT 传递 / 禁止重复 groupId:artifactId:version 声明 |
| Mid | 统一 Jacoco 报告聚合 | 父 POM 聚合覆盖率报告导出 HTML |
| Low | Flatten Plugin | 发布时生成扁平可消费 POM |

---

## 9. 依赖可视化（简化）

```
patra-parent
  ├─ spring-boot-dependencies (import)
  ├─ spring-cloud-dependencies (import)
  ├─ spring-cloud-alibaba-dependencies (import)
  ├─ rocketmq-spring-boot-starter (managed)
  ├─ mybatis-plus-spring-boot3-starter (managed)
  ├─ mapstruct / lombok (managed + processors)
  ├─ hutool-core
  └─ patra-* 内部模块 (统一版本)
```

---

## 10. 参考命令

```bash
# 全仓编译（跳过测试）
mvn -T1C -DskipTests compile

# 查看依赖树（定位冲突）
mvn dependency:tree -Dincludes=com.papertrace

# Enforcer（后续可加）
mvn -Denforcer.skip=false validate
```

---

提交变更请附：升级目的 / 影响范围 / 回滚方式（恢复属性旧值）。