环境检查：
- `java -version`（要求 Java 21）
- `mvn -v`

全仓快速编译（不打包）：
- `mvn -q -T 1C -DskipTests compile`

单模块开发：
- 进入子模块目录后：`mvn -q -DskipTests compile`
- 单元测试：`mvn -q test`
- 快速回归（含检查）：`mvn -q -DskipITs test`

打包：
- `mvn clean package -DskipTests`（如需跑测移除 `-DskipTests`）

示例：
- patra-registry：`cd patra-registry && mvn -q clean test`

常见服务：
- Spring Boot 本地启动（按模块）：`mvn spring-boot:run`

注意：优先单模块构建与测试，只有明确需要时使用全仓命令。