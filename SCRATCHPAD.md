# SCRATCHPAD.md - 工作记忆

> **状态**：✅ 已完成
> **任务名称**：Spring Boot 4.0.1 升级后优化
> **开始时间**：2026-01-12
> **完成时间**：2026-01-12
> **更新者**：Claude

---

## 🎯 当前任务

**目标**：完成 Spring Boot 4.0.1 升级后的可选优化项，提升代码质量和消除警告

**进度**：
- [x] 配置 Mockito Agent 消除测试警告
- [x] 迁移 JSpecify nullability 注解（5 个文件）
- [x] 运行测试验证优化效果（BUILD SUCCESS）

---

## 📋 关键决策

| 日期 | 决策 | 原因 |
|------|------|------|
| 2026-01-12 | Mockito Agent 使用本地仓库路径配置 | `maven-dependency-plugin` 的 `properties` goal 在某些模块无法正确解析，改用 `${settings.localRepository}` 直接引用 jar 路径 |
| 2026-01-12 | GlobalRestExceptionHandler 保留 Spring @NonNull | JSpecify @NonNull 是类型注解，不能用于方法参数声明处；该方法是 `@Override`，需与父类签名一致 |

---

## ✅ 已完成的优化

### 1. Mockito Agent 配置

**问题**：测试运行时出现警告
```
Mockito is currently self-attaching to enable the inline-mock-maker.
This will no longer work in future releases of the JDK.
```

**解决方案**：在 `patra-parent/pom.xml` 的 surefire 配置中添加 `-javaagent`
```xml
<argLine>
  -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar
  --add-opens java.base/java.lang=ALL-UNNAMED
</argLine>
```

### 2. JSpecify Nullability 注解迁移

**迁移的文件**（5 个）：
- `patra-spring-boot-starter-provenance/.../PubmedPublicationProcessor.java`
- `patra-spring-boot-starter-rest-client/.../DownloadOptions.java`
- `patra-spring-boot-starter-rest-client/.../DownloadRequest.java`
- `patra-spring-boot-starter-rest-client/.../DefaultDownloadClient.java`
- `patra-spring-boot-starter-rest-client/.../DownloadClient.java`

**未迁移的文件**（1 个）：
- `patra-spring-boot-starter-web/.../GlobalRestExceptionHandler.java`
  - 原因：使用 `@NonNull` 的是 `@Override` 方法参数，需与父类签名保持一致

---

## 📁 变更文件汇总

**修改**：
- `patra-parent/pom.xml` - 配置 Mockito Agent
- `patra-spring-boot-starter-provenance/.../PubmedPublicationProcessor.java` - JSpecify 迁移
- `patra-spring-boot-starter-rest-client/.../DownloadOptions.java` - JSpecify 迁移
- `patra-spring-boot-starter-rest-client/.../DownloadRequest.java` - JSpecify 迁移
- `patra-spring-boot-starter-rest-client/.../DefaultDownloadClient.java` - JSpecify 迁移
- `patra-spring-boot-starter-rest-client/.../DownloadClient.java` - JSpecify 迁移

---

> **下一步**：可使用 `/new-task` 清理此工作记忆并开始新任务
