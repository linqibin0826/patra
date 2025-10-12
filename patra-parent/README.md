# patra-parent — Parent POM

> **Maven parent POM** providing centralized dependency management and plugin configuration for all Papertrace modules.

---

## 📌 Purpose

Provides **consistent** dependency versions and build configuration:
- Dependency management (Spring Boot, MyBatis-Plus, etc.)
- Plugin management (compiler, surefire, jacoco, etc.)
- Java version enforcement (Java 21)
- Encoding standards (UTF-8)
- Maven properties (versions, flags)

---

## 🔧 Managed Dependencies

### Spring Ecosystem

```xml
<dependencyManagement>
    <dependencies>
        <!-- Spring Boot BOM -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.2.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- Spring Cloud BOM -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2023.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Papertrace Modules

All internal modules inherit versions from parent:

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common</artifactId>
    <version>${project.version}</version>  <!-- 0.1.0-SNAPSHOT -->
</dependency>
```

### Key Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| **MyBatis-Plus** | 3.5.5 | ORM framework |
| **MapStruct** | 1.5.5 | Object mapping |
| **Hutool** | 5.8.25 | Java utilities |
| **Resilience4j** | 2.2.0 | Resilience patterns |
| **SkyWalking** | 9.5.0 | Distributed tracing |

---

## 🔨 Plugin Management

### Maven Compiler Plugin

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

### Maven Surefire Plugin

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

### JaCoCo (Code Coverage)

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
                        <minimum>0.75</minimum>  <!-- 75% coverage -->
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</plugin>
```

---

## 🚀 Usage

### In Child Modules

```xml
<parent>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../patra-parent/pom.xml</relativePath>
</parent>

<artifactId>patra-{module}</artifactId>

<dependencies>
    <!-- No need to specify versions (inherited from parent) -->
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

## 📦 Build Commands

### Build All Modules

```bash
mvn clean install
```

### Skip Tests

```bash
mvn clean install -DskipTests
```

### Run Tests with Coverage

```bash
mvn clean verify
# Coverage report: target/site/jacoco/index.html
```

### Update All Versions

```bash
mvn versions:set -DnewVersion=0.2.0-SNAPSHOT
mvn versions:commit
```

---

## 🔗 Module Hierarchy

```
patra-parent (this)
├─ patra-common
├─ patra-expr-kernel
├─ patra-spring-boot-starter-*
├─ patra-spring-cloud-starter-*
├─ patra-registry
├─ patra-ingest
└─ patra-gateway-boot
```

All modules declare `patra-parent` as parent.

---

## 📊 Properties

### Java Version

```xml
<properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
</properties>
```

### Encoding

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
</properties>
```

---

## 🔗 Related Documentation

- [Main README](../README.md)
- [Architecture Guide](../docs/ARCHITECTURE.md)

---

**Last Updated**: 2025-01-12
