# 数据可视化规范（Obsidian Charts）

## 强制规则

1. 禁止使用 ASCII 艺术字符（`█▓░│─`）
2. 图表必须有 `title` 字段
3. 使用 `labelColors: true` 自动着色

## 图表类型

| 类型 | `type` 值 | 适用场景 |
|------|-----------|----------|
| 柱状图 | `bar` | 分类对比、频率分布 |
| 折线图 | `line` | 趋势变化、时间序列 |
| 饼图 | `pie` | 占比分布（≤7 项） |
| 环形图 | `doughnut` | 占比分布（中心可标注） |
| 雷达图 | `radar` | 多维度评估 |

**不支持**：散点图、热力图、箱线图（使用外部工具生成 PNG 嵌入）

## 基础语法

````markdown
```chart
type: bar
labels: [Q1, Q2, Q3, Q4]
series:
  - title: 2024年
    data: [120, 150, 180, 200]
title: 季度销售额
width: 80%
labelColors: true
beginAtZero: true
```
````

## 配置选项

| 选项 | 说明 | 示例 |
|------|------|------|
| `type` | 图表类型（必填） | `bar`, `line`, `pie` |
| `title` | 图表标题 | `"销售趋势"` |
| `width` | 图表宽度 | `"80%"` |
| `labelColors` | 自动着色 | `true` |
| `beginAtZero` | Y 轴从 0 开始 | `true` |
| `fill` | 折线图填充 | `true` |
| `tension` | 曲线平滑度 | `0.2` |

## 自定义颜色

```yaml
series:
  - title: 测试结果
    data: [85, 10, 5]
    backgroundColor:
      - "#22c55e"
      - "#ef4444"
      - "#f59e0b"
```

## 故障排查

| 问题 | 解决 |
|------|------|
| 图表不渲染 | 检查代码块语法 |
| YAML 解析错误 | 2 空格缩进，冒号后加空格 |
| 数据不显示 | data 与 labels 长度一致 |
| 图表过窄 | 设置 `width: 80%` |
