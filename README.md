# Patra Monorepo

医学出版物数据平台 —— 采集、解析、存储来自 PubMed / EPMC / Crossref 等 10+ 外部数据源的文献与期刊数据。

## Workspace 布局

| 顶层目录 | 职责 | 技术栈 |
|---|---|---|
| **linqibin-commons/** | 个人通用 Spring Boot 基建（commons + 11 starter） | Java 25 / Spring Boot 4 / Gradle |
| **patra-starters/** | Patra 项目专属 starter（expr / provenance） | Java 25 / Spring Boot 4 |
| **patra-api/** | 后端业务服务（微服务 + 六边形 + DDD） | Java 25 / Spring Boot 4 |
| **patra-portal/** | 前端管理控制台 | Next.js 15 / React 19 / TypeScript 5 / Tailwind v4 |
| **patra-infra/** | 基建配置 | Docker Compose / Bash / launchd |
| **build-logic/** | Gradle convention plugins 与依赖管理 | Kotlin DSL |
| **docs/** | 项目文档（spec / plan / 决策记录） | HTML（自有 journal 主题） |

## Gradle 坐标

| 子树 | group |
|---|---|
| linqibin-commons/* | `dev.linqibin.commons` |
| patra-starters/* + patra-api/* | `dev.linqibin.patra` |

**边界**：linqibin-commons/* 由 `linqibin.boundary-check` convention plugin 强制禁止依赖任何 `:patra-*` 模块（保留未来抽离独立 repo 的通路）。验证方式：`./gradlew :<commons-module>:checkBoundary`。

## 常用命令

```bash
./gradlew clean assemble           # 构建全部模块（不跑测试 / lint）
./gradlew clean build              # 构建 + 测试 + SpotBugs / Spotless / Jacoco
./gradlew check                    # 仅运行测试 + 代码质量
./gradlew spotlessApply            # 格式化代码
./gradlew :patra-catalog:patra-catalog-boot:bootRun  # 启动 catalog 服务
./gradlew :<commons-module>:checkBoundary            # 校验依赖边界
./gradlew publishToMavenLocal      # 发布到 ~/.m2 本地
```

## 文档

- 设计规格：`docs/patra/specs/`
- 实现计划：`docs/patra/plans/`
- 文档样式：`docs/patra/styles/journal.css` + `plan-extras.css`
