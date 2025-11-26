# ADR-002: Batch 可观测性迁移至 Spring 原生支持

## 状态

已采纳 (2025-11-26)

## 背景

自定义 `BatchObservationJobListener` 需要用户手动注入到每个 Job 定义中，使用繁琐且容易遗漏。Spring Batch 5+ 已提供原生可观测性支持。

## 决策

使用 Spring Batch 原生 `BatchObservabilityBeanPostProcessor` 替代自定义监听器，在 `BatchAutoConfiguration` 中注册，实现零配置集成。

## 原因

1. **零用户侵入** — 无需修改任何 Job 定义代码
2. **功能更完整** — 原生支持 Job + Step 级别的 trace/span（自定义仅支持 Job）
3. **减少维护** — 删除 192 行自定义代码
4. **官方支持** — 使用 Spring Batch 推荐方案，随版本升级自动获得增强

## 后果

- ✅ 用户定义 Job 时完全不需要关心可观测性配置
- ✅ Step 执行自动创建 span，追踪粒度更细
- ⚠️ 标签格式改为 Spring 标准格式（`spring.batch.job.*`）
