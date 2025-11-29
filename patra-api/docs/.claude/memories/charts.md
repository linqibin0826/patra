# 数据可视化规范（Obsidian Charts）

> **适用场景**：柱状图、折线图、饼图、雷达图等统计类图表。

---

## Mermaid vs Charts 选型指南

| 需求 | 推荐方案 | 说明 |
|------|----------|------|
| 流程图、架构图 | **Mermaid** | 结构化关系表达 |
| 时序图、状态图 | **Mermaid** | 系统交互、状态机 |
| ER 图、类图 | **Mermaid** | 数据模型、对象结构 |
| 柱状图、折线图 | **Charts** | 数值对比、趋势分析 |
| 饼图、环形图 | **Charts** | 占比分布 |
| 雷达图 | **Charts** | 多维度评估 |
| 直方图 | **Charts** (`bar`) | 频率分布 |
| 散点图 | **外部工具** | Charts 不原生支持 |

---

## 强制规则

1. **禁止使用 ASCII 艺术字符**（`█▓░│─` 等）绘制图表
2. **优先使用 Charts 插件**，仅当插件不支持时考虑外部图片
3. **图表必须有标题** — 使用 `title` 字段说明图表含义
4. **颜色使用语义化** — 通过 `labelColors: true` 自动着色，或自定义对比度高的颜色

---

## Charts 插件语法

### 基础结构

````markdown
```chart
type: bar
labels: [标签1, 标签2, 标签3]
series:
  - title: 系列名称
    data: [值1, 值2, 值3]
title: 图表标题
width: 80%
labelColors: true
```
````

### 图表类型

| 类型 | `type` 值 | 适用场景 |
|------|-----------|----------|
| 柱状图 | `bar` | 分类对比、频率分布 |
| 折线图 | `line` | 趋势变化、时间序列 |
| 饼图 | `pie` | 占比分布（≤7 项） |
| 环形图 | `doughnut` | 占比分布（中心可标注） |
| 雷达图 | `radar` | 多维度评估 |
| 极坐标图 | `polarArea` | 数值与角度双编码 |

---

## 常用模板

### 柱状图（分类对比）

````markdown
```chart
type: bar
labels: [Q1, Q2, Q3, Q4]
series:
  - title: 2024年
    data: [120, 150, 180, 200]
  - title: 2025年
    data: [140, 170, 210, 250]
title: 季度销售额对比
width: 80%
labelColors: true
beginAtZero: true
```
````

### 折线图（趋势分析）

````markdown
```chart
type: line
labels: [1月, 2月, 3月, 4月, 5月, 6月]
series:
  - title: 用户数
    data: [1000, 1200, 1500, 1800, 2200, 2800]
  - title: 活跃用户
    data: [800, 950, 1200, 1500, 1900, 2400]
title: 用户增长趋势
width: 80%
labelColors: true
fill: false
tension: 0.2
```
````

### 饼图（占比分布）

````markdown
```chart
type: pie
labels: [Java, Python, Go, Rust, Other]
series:
  - title: 语言占比
    data: [45, 25, 15, 10, 5]
title: 代码库语言分布
width: 60%
labelColors: true
```
````

### 雷达图（多维评估）

````markdown
```chart
type: radar
labels: [性能, 可维护性, 安全性, 可扩展性, 易用性]
series:
  - title: 方案A
    data: [85, 70, 90, 75, 80]
  - title: 方案B
    data: [70, 85, 75, 90, 85]
title: 技术方案评估
width: 70%
labelColors: true
```
````

### 环形图（带中心标注）

````markdown
```chart
type: doughnut
labels: [已完成, 进行中, 待开始]
series:
  - title: 任务状态
    data: [65, 25, 10]
title: 项目进度
width: 50%
labelColors: true
```
````

---

## 配置选项

### 通用选项

| 选项 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `type` | string | 图表类型（必填） | `bar`, `line`, `pie` |
| `title` | string | 图表标题 | `"销售趋势"` |
| `width` | string | 图表宽度 | `"80%"`, `"500px"` |
| `labelColors` | boolean | 自动着色 | `true` |
| `beginAtZero` | boolean | Y 轴从 0 开始 | `true` |

### 折线图专用

| 选项 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `fill` | boolean | 填充曲线下方区域 | `true` |
| `tension` | number | 曲线平滑度（0-1） | `0.2` |

### 自定义颜色

````markdown
```chart
type: bar
labels: [成功, 失败, 跳过]
series:
  - title: 测试结果
    data: [85, 10, 5]
    backgroundColor:
      - "#22c55e"
      - "#ef4444"
      - "#f59e0b"
title: 测试执行统计
width: 60%
```
````

---

## 与 Dataview 集成

Charts 插件可与 Dataview 结合，从笔记元数据动态生成图表：

````markdown
```dataviewjs
const pages = dv.pages('"devlog/daily"')
  .where(p => p.date >= dv.date("2025-01-01"))
  .sort(p => p.date);

const labels = pages.map(p => p.date.toFormat("MM-dd")).array();
const data = pages.map(p => p.commits || 0).array();

const chartData = {
  type: 'line',
  data: {
    labels: labels,
    datasets: [{
      label: '每日提交数',
      data: data,
      borderColor: '#3b82f6',
      tension: 0.2
    }]
  }
};

window.renderChart(chartData, this.container);
```
````

---

## 故障排查

| 问题 | 原因 | 解决 |
|------|------|------|
| 图表不渲染 | 插件未安装或语法错误 | 检查代码块语法是否正确 |
| YAML 解析错误 | 缩进不正确 | 使用 2 空格缩进，冒号后加空格 |
| 数据不显示 | data 与 labels 长度不匹配 | 确保数组长度一致 |
| 颜色不显示 | 缺少 `labelColors` | 添加 `labelColors: true` |
| 图表过窄 | 默认宽度限制 | 设置 `width: 80%` 或更大 |

---

## 不支持的图表类型

以下类型 Charts 插件不原生支持，建议使用外部工具生成后嵌入：

| 类型 | 替代方案 |
|------|----------|
| 散点图 | Python matplotlib → PNG |
| 热力图 | Python seaborn → PNG |
| 箱线图 | R ggplot2 → PNG |
| 瀑布图 | Excel → PNG |
| 桑基图 | 外部工具 → PNG |

嵌入语法：`![[charts/filename.png|500]]`
