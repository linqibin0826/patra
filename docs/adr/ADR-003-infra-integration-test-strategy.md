# ADR-003: Infra 层采用集成测试替代单元测试

## 状态
已采纳

## 背景
Infra 层 Repository 实现的单元测试价值有限——Mock Mapper 只能验证方法调用，无法验证 SQL 语句、类型映射、MyBatis-Plus 拦截器等实际行为。测试通过不代表生产环境正常工作。

## 决策
Infra 层 Repository 采用 TestContainers + MySQL 集成测试，废弃 Mock-based 单元测试。

## 原因
1. **真实验证**：测试实际 SQL 执行、字段映射、JSON 序列化
2. **发现隐藏问题**：如 `BIGINT(20)` 废弃警告、TypeHandler 配置错误
3. **重构安全**：修改 Mapper XML 时有真实反馈
4. **测试代码更简洁**：无需复杂 Mock 设置

## 后果
- ✅ 测试可靠性大幅提升，能发现实际集成问题
- ✅ 配置统一到 `starter-test` 模块，各服务复用
- ⚠️ 首次运行需拉取 Docker 镜像（后续 JVM 级复用）
- ⚠️ 单次测试耗时约 15s（可接受，换取真实验证）
