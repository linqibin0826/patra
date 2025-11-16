# patra-parent

## 概述

`patra-parent` 是 **Patra 项目的 Maven 父 POM**,为所有子模块提供集中的依赖版本管理、插件配置和构建规范。它确保整个代码库使用一致的技术栈版本、编译设置和代码质量标准。

**核心价值**: 单一依赖版本来源(Single Source of Truth),简化子模块配置,避免版本冲突,统一构建行为。

## 核心职责

- **依赖版本管理**: 统一管理 Spring Boot、Spring Cloud、MyBatis-Plus 等核心框架版本
- **插件配置**: 配置编译器、测试、代码覆盖率、静态分析等插件
- **版本强制**: 强制使用 Java 25 和 Maven 3.8.1+
- **编码规范**: 统一 UTF-8 编码和 Google Java Format 代码风格
- **质量检查**: 配置 JaCoCo、SpotBugs 等代码质量工具

## 依赖管理

### Spring 生态系统

| 依赖 | 版本 | 说明 |
|------|------|------|
| **Spring Boot** | 3.5.7 | Spring Boot 框架核心 |
| **Spring Cloud** | 2025.0.0 | Spring Cloud 微服务组件 |
| **Spring Cloud Alibaba** | 2025.0.0.0 | Nacos、Sentinel 等组件 |

### ORM 与数据访问

| 依赖 | 版本 | 说明 |
|------|------|------|
| **MyBatis-Plus** | 3.5.12 | 增强版 MyBatis ORM 框架 |
| **MapStruct** | 1.6.3 | 对象映射工具 |

### 工具库

| 依赖 | 版本 | 说明 |
|------|------|------|
| **Hutool** | 5.8.25 | Java 工具类库 |
| **Guava** | 31.1-jre | Google 核心 Java 库 |
| **Lombok** | 1.18.40 | 简化 Java 代码(注解处理器) |

### 弹性与监控

| 依赖 | 版本 | 说明 |
|------|------|------|
| **Resilience4j** | 2.2.0 | 容错和弹性模式库 |
| **SkyWalking** | 9.5.0 | 分布式追踪 APM 工具 |

### 消息与任务调度

| 依赖 | 版本 | 说明 |
|------|------|------|
| **RocketMQ** | 5.3.1 | 分布式消息队列 |
| **XXL-Job** | 3.2.0 | 分布式任务调度框架 |

### 云服务

| 依赖 | 版本 | 说明 |
|------|------|------|
| **AWS SDK** | 2.25.36 | AWS 云服务 SDK |
| **OpenFeign** | 13.1 | HTTP 客户端 |

### Patra 内部模块

所有内部模块版本统一为 `${project.version}` (当前 `0.1.0-SNAPSHOT`):

- **patra-common-core**: 核心工具类和基础抽象
- **patra-common-storage**: 存储相关工具
- **patra-common-model**: 共享数据模型
- **patra-expr-kernel**: 表达式引擎
- **patra-spring-boot-starter-\***: Spring Boot 集成 starter
- **patra-spring-cloud-starter-\***: Spring Cloud 集成 starter
- **patra-registry-api / patra-storage-api**: 微服务 API 模块

### 测试框架

| 依赖 | 版本 | 说明 |
|------|------|------|
| **JUnit 5** | 5.10.2 | 单元测试框架 |
| **Testcontainers** | 1.20.4 | Docker 容器测试支持 |
| **ArchUnit** | 1.3.0 | 架构规则测试 |

## 插件配置

### 编译器插件 (maven-compiler-plugin)

**版本**: 3.14.1

**配置**:
- **Java 版本**: 25(使用 `--release` 参数确保兼容性)
- **编码**: UTF-8
- **注解处理器**: Lombok + MapStruct(按顺序配置,确保兼容性)
- **参数保留**: 启用 `-parameters` 以支持 Spring 参数名识别

### 测试插件 (maven-surefire-plugin)

**版本**: 3.2.5

**配置**:
- **JVM 参数**: 添加 `--add-opens` 以支持 Java 模块化和 CGLIB 代理
- **JaCoCo 集成**: 保留 `@{argLine}` 占位符以注入 JaCoCo 参数

### 代码覆盖率 (jacoco-maven-plugin)

**版本**: 0.8.12

**执行阶段**:
- **prepare-agent**: 在 `process-test-classes` 阶段准备 JaCoCo 代理
- **report**: 在 `test` 阶段生成覆盖率报告(输出到 `target/site/jacoco/`)
- **check**: 默认禁用,可通过 `./mvnw jacoco:check` 手动执行

**覆盖率要求**: 70% 行覆盖率(可在子模块中覆盖)

### 静态分析 (spotbugs-maven-plugin)

**版本**: 4.8.6.4

**配置**:
- **分析级别**: Max(最大努力)
- **报告阈值**: Medium(报告中高严重性问题)
- **失败策略**: `failOnError=true`(发现问题时构建失败)
- **排除规则**: 使用根目录 `spotbugs-exclude.xml`
- **输出格式**: XML + HTML 报告(输出到 `target/spotbugs/`)

### 代码格式化 (fmt-maven-plugin)

**版本**: 2.29(使用 google-java-format 1.29.0)

**配置**:
- **代码风格**: Google Java Format(GOOGLE 风格)
- **执行方式**: 手动触发 `./mvnw fmt:format`(默认不在编译时自动格式化)
- **用途**: 统一代码风格,避免格式化引起的代码冲突

### 版本强制 (maven-enforcer-plugin)

**版本**: 3.4.1

**强制规则**:
- **Java 版本**: 要求 Java 25 或更高版本
- **Maven 版本**: 要求 Maven 3.8.1 或更高版本
- **依赖收敛**: 在 `verify` 阶段检查依赖版本冲突(避免传递依赖版本不一致)

## 使用方式

### 在子模块中继承父 POM

```xml
<project>
    <parent>
        <groupId>com.patra</groupId>
        <artifactId>patra-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../patra-parent/pom.xml</relativePath>
    </parent>

    <artifactId>patra-{module-name}</artifactId>

    <dependencies>
        <!-- 无需指定版本,从父 POM 继承 -->
        <dependency>
            <groupId>com.patra</groupId>
            <artifactId>patra-common-core</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- 测试依赖 -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### 常用构建命令

```bash
# 编译所有模块
mvn clean compile

# 编译并安装到本地仓库
mvn clean install

# 跳过测试
mvn clean install -DskipTests

# 运行测试并生成覆盖率报告
mvn clean test
# 报告位置: target/site/jacoco/index.html

# 代码格式化
mvn fmt:format

# 静态分析
mvn spotbugs:check

# 完整验证(包括依赖收敛检查)
mvn clean verify
```

### 版本更新

```bash
# 更新项目版本
mvn versions:set -DnewVersion=0.2.0-SNAPSHOT

# 提交版本变更
mvn versions:commit

# 回退版本变更
mvn versions:revert
```

## 版本要求

- **Java**: 25 或更高版本
- **Maven**: 3.8.1 或更高版本
- **编码**: UTF-8

**检查方式**: 在 `validate` 阶段,Maven Enforcer Plugin 会自动检查版本要求,不满足要求时构建会立即失败并提示错误信息。

---

**最后更新**: 2025-11-03
