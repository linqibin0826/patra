# patra-parent — 父 POM

> **Maven 父 POM**,为所有 Papertrace 模块提供集中的依赖管理和插件配置。

---

## 📌 目的

提供**一致的**依赖版本和构建配置:
- 依赖管理(Spring Boot、MyBatis-Plus 等)
- 插件管理(编译器、surefire、jacoco 等)
- Java 版本强制(Java 25)
- 编码标准(UTF-8)
- Maven 属性(版本、标志)

---

## 🔧 管理的依赖

### Spring 生态系统

```xml
<dependencyManagement>
    <dependencies>
        <!-- Spring Boot BOM -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.2.4</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- Spring Cloud BOM -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2023.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Papertrace 模块

所有内部模块从父 POM 继承版本:

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common</artifactId>
    <version>${project.version}</version>  <!-- 0.1.0-SNAPSHOT -->
</dependency>
```

### 核心库

| 库 | 版本 | 用途 |
|---------|---------|---------|
| **MyBatis-Plus** | 3.5.5 | ORM 框架 |
| **MapStruct** | 1.5.5 | 对象映射 |
| **Hutool** | 5.8.25 | Java 工具库 |
| **Resilience4j** | 2.2.0 | 弹性模式 |
| **SkyWalking** | 9.5.0 | 分布式追踪 |

---

## 🔨 插件管理

### Maven 编译器插件

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.12.1</version>
    <configuration>
        <source>21</source>
        <target>21</target>
        <encoding>UTF-8</encoding>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

### Maven Surefire 插件

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <argLine>-Dfile.encoding=UTF-8</argLine>
    </configuration>
</plugin>
```

### JaCoCo (代码覆盖率)

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <configuration>
        <rules>
            <rule>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.75</minimum>  <!-- 75% 覆盖率 -->
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</plugin>
```

---

## 🚀 用法

### 在子模块中

```xml
<parent>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../patra-parent/pom.xml</relativePath>
</parent>

<artifactId>patra-{module}</artifactId>

<dependencies>
    <!-- 无需指定版本(从父 POM 继承) -->
    <dependency>
        <groupId>com.papertrace</groupId>
        <artifactId>patra-common</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

---

## 📦 构建命令

### 构建所有模块

```bash
mvn clean install
```

### 跳过测试

```bash
mvn clean install -DskipTests
```

### 运行测试并生成覆盖率报告

```bash
mvn clean verify
# 覆盖率报告: target/site/jacoco/index.html
```

### 更新所有版本

```bash
mvn versions:set -DnewVersion=0.2.0-SNAPSHOT
mvn versions:commit
```

---

## 🔗 模块层次结构

```
patra-parent (本模块)
├─ patra-common
├─ patra-expr-kernel
├─ patra-spring-boot-starter-*
├─ patra-spring-cloud-starter-*
├─ patra-registry
├─ patra-ingest
└─ patra-gateway-boot
```

所有模块将 `patra-parent` 声明为父 POM。

---

## 📊 属性

### Java 版本

```xml
<properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
</properties>
```

### 编码

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
</properties>
```

---

## 🔗 相关文档

- [主 README](../README.md)
- [架构指南](../docs/ARCHITECTURE.md)

---

**最后更新**: 2025-01-12
