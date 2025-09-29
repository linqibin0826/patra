# patra-parent

Maven 多模块父 POM，统一依赖版本与构建策略，是仓库所有子模块的基准。

## 1. 模块定位
- **服务/组件作用**：集中管理外部 BOM、内部模块版本和编译插件，保证依赖一致性与构建可重复
- **主要消费者**：仓库内全部模块（通过 `<parent>` 继承），以及需要复用 Papertrace 依赖矩阵的外部组件
- **架构边界**：仅负责构建治理；不引入业务依赖、不提供运行时代码，禁止在子模块再次声明被管理的 `<version>`

## 2. 核心能力
- **版本收敛**：锁定 Spring Boot / Spring Cloud / Alibaba Cloud、MyBatis-Plus、RocketMQ、Hutool、XXL-Job、MapStruct、Lombok 等主版本
- **Java/编码约束**：统一 `java.version=21` 与 UTF-8 编码
- **注解处理器统一**：`maven-compiler-plugin` 集中声明 Lombok、MapStruct Processor，子模块无需重复配置
- **内部 BOM**：对 `patra-common`、`patra-expr-kernel`、自研 Starters 统一管理 `${project.version}`，避免版本漂移

## 3. 分层结构与依赖
- 核心文件：`patra-parent/pom.xml`
- 关键属性示例：
  | 属性 | 说明 |
  |------|------|
  | `java.version` | 控制编译目标，与 `maven.compiler.release` 对齐 |
  | `spring-boot.version` | 主框架版本 |
  | `spring-cloud.version` / `spring-cloud-alibaba.version` | 微服务相关 BOM |
  | `mybatis-plus.version` / `rocketmq.spring.version` / `hutool.version` | 技术栈配套库 |
  | `mapstruct.version` / `lombok.version` | 映射与样板代码工具链 |

- 依赖策略：
  | 分类 | 策略 |
  |------|------|
  | 第三方 BOM | 通过 `<dependencyManagement>` import 官方 BOM，避免手动维护零散版本 |
  | 内部模块 | 统一锁定 `${project.version}`，禁止子模块单独升级 |
  | 可选依赖 | 仅 MapStruct 标记 optional；Lombok 不标记 optional 以保证编译期生效 |
  | 测试依赖 | 仅提供 JUnit Jupiter 基础，其余按需在子模块声明 |

## 4. 运行与配置
- **子模块继承模板**：
  ```xml
  <parent>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../patra-parent/pom.xml</relativePath>
  </parent>
  ```
- **禁止操作**：
  1. 在子模块重新声明已管理依赖的 `<version>`
  2. 直接修改 JDK/编码设置
  3. 引入非受控 BOM 覆盖父 POM
- **常用命令**：
  ```bash
  # 全仓编译（跳过测试）
  mvn -T1C -DskipTests compile
  # 查看依赖树 / 排查冲突
  mvn dependency:tree -Dincludes=com.papertrace
  ```

## 5. 观测与运维
- 该模块仅影响构建流程；建议在 CI 中固定使用父 POM 版本并开启 `mvn -Denforcer.skip=false validate` 作为质量门禁
- 升级依赖前需在 CI 运行关键模块测试（core/web/feign 等）验证兼容性

## 6. 测试策略
- 无运行时代码；通过以下方式保障稳定性：
  - 执行 `mvn -T1C -DskipTests compile` 验证依赖树
  - 在升级流程中运行重点模块单测或冒烟用例
  - 结合 `mvn dependency:analyze` 检测未使用依赖

## 7. Roadmap 与风险
| 优先级 | 项目 | 描述 |
|--------|------|------|
| High | Spotless / Checkstyle 统一 | 引入统一格式化与静态检查配置，减少团队差异 |
| High | Enforcer 规则强化 | 禁止 SNAPSHOT 传递、禁止重复 GAV 声明 |
| Mid | Jacoco 报告聚合 | 父 POM 聚合各模块覆盖率并导出 HTML |
| Low | Flatten Plugin | 发布时生成扁平 POM 以供部署/开源消费 |

主要风险：升级框架版本时需关注兼容矩阵（Boot 与 Cloud 版本匹配）、注解处理器变化及 transitive 依赖冲突。

## 8. 参考资料
- 依赖可视化（简版）：
  ```
  patra-parent
    ├─ spring-boot-dependencies (import)
    ├─ spring-cloud-dependencies (import)
    ├─ spring-cloud-alibaba-dependencies (import)
    ├─ rocketmq-spring-boot-starter (managed)
    ├─ mybatis-plus-spring-boot3-starter (managed)
    ├─ mapstruct / lombok (managed + processors)
    ├─ hutool-core
    └─ patra-* 内部模块
  ```
- Roadmap 操作指南：在升级前参考 `docs/templates/root-readme-outline.md` 中的构建章节
- 相关模块：`patra-common/README.md`（复用的公共抽象）、`docs/overview/architecture.md`（总体架构）
