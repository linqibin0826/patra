# Registry 服务专题（已归档）

> 本页已合并至：`docs/modules/registry/deep-dive.md`。如需最新内容请跳转至专题页。

本专题补充 `patra-registry/README.md`，聚焦配置治理、快照生成与常见运维操作。

## 1. 子域快速对照
- **Dictionary**：提供类型、条目与别名视图，支持状态过滤与默认项校验
- **Provenance**：组合来源配置切片（Window、HTTP、分页、限流、凭证等）形成采集参数
- **Expr**：合成字段能力、渲染模板、API 参数映射的快照

## 2. 配置生效流程
1. 在管理端写入配置（或通过 SQL 种子导入）
2. 数据按 `effective_from` / `effective_to` 控制时间窗口
3. Registry 读取时依据 `Scope` 优先级 SOURCE < TASK 合并
4. 最终快照通过 Feign 暴露给 ingest / 分析服务

## 3. 变更指南
- 修改配置应新增版本（append-only），避免直接更新历史记录
- 对关键配置使用事务提交并记录操作人（建议落库）
- 批量导入可参考 `sql/` 目录样例，执行前先在测试库验证

## 4. 数据一致性检查
- 建议建立定时任务检测以下风险：
  - 同一 scope + task + operation 的时间窗口重叠
  - Dictionary 默认项超过 1 条
  - Expr 能力缺失必需字段
- 若发现冲突，可在 `operations/troubleshooting.md` 记录处理案例

## 5. 常用命令
```bash
# 校验模块测试
cd patra-registry && ../../mvnw -q test

# 单独运行 Boot 服务（本地）
../../mvnw -pl patra-registry/patra-registry-boot -am spring-boot:run
```

## 6. 延伸阅读
- 模块 README：`patra-registry/README.md`
- SQL 种子：`docs/modules/registry/sql`
- 错误规范：`docs/standards/platform-error-handling.md`
