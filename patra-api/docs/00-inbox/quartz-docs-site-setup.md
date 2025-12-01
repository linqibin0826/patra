---
title: 使用 Quartz 搭建 Obsidian 文档站点
date: 2025-12-01
tags:
  - quartz
  - obsidian
  - github-pages
  - documentation
---

# 使用 Quartz 搭建 Obsidian 文档站点

## 背景

项目文档使用 Obsidian 管理，存放在 `docs/` 目录。希望将文档发布到网站，以便：
1. 让 Google NotebookLM 等 AI 工具能够访问和分析
2. 团队成员无需 Obsidian 也能浏览文档
3. 文档变更后自动发布

## 方案选型

### 尝试 1：Jekyll（失败）

最初选择 GitHub Pages 默认的 Jekyll，遇到两个问题：

**问题 1：Liquid 模板冲突**

文档中包含 Prometheus/Grafana 告警规则模板：
```yaml
annotations:
  summary: "高错误率: {{ $value | printf \"%.2f\" }}%"
```

Jekyll 使用 Liquid 模板引擎，`{{ }}` 语法冲突导致构建失败。

虽然可以通过 `render_with_liquid: false` 禁用，但还有更大的问题：

**问题 2：不支持 Wiki Links**

Obsidian 的 `[[文件名]]` 链接语法，Jekyll 无法识别，所有内部链接都变成死链。

### 尝试 2：Quartz（成功）

[Quartz](https://quartz.jzhao.xyz/) 是专为 Obsidian 设计的静态站点生成器：
- 原生支持 Wiki Links `[[]]`
- 原生支持 Mermaid 图表
- 支持关系图谱、反向链接
- 支持中文搜索

## 实施步骤

### 1. 创建 Quartz 仓库

```bash
# 克隆 Quartz 模板
git clone https://github.com/jackyzha0/quartz.git Patra-docs
cd Patra-docs

# 删除原有 git 历史
rm -rf .git
git init
git remote add origin git@github.com:linqibin0826/Patra-docs.git

# 复制文档内容到 content 目录
cp -r ../Patra-api/docs/* content/
rm -rf content/.obsidian

# 安装依赖
npm install
```

### 2. 配置 Quartz

**`quartz.config.ts`**：
```typescript
const config: QuartzConfig = {
  configuration: {
    pageTitle: "Patra 文档",
    pageTitleSuffix: " | Patra",
    enableSPA: true,
    enablePopovers: true,
    locale: "zh-CN",
    baseUrl: "linqibin0826.github.io/Patra-docs",
    ignorePatterns: ["private", "templates", ".obsidian", "00-inbox"],
    // ... 其他配置
  },
}
```

### 3. 部署到 GitHub Pages

创建 `.github/workflows/deploy.yml`：

```yaml
name: Deploy Quartz site to GitHub Pages

on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: "pages"
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: actions/setup-node@v4
        with:
          node-version: 22

      - name: Install Dependencies
        run: npm ci

      - name: Build Quartz
        run: npx quartz build

      - name: Upload artifact
        uses: actions/upload-pages-artifact@v3
        with:
          path: public

  deploy:
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    runs-on: ubuntu-latest
    needs: build
    steps:
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

在 GitHub 仓库设置中：Settings → Pages → Source 选择 **GitHub Actions**。

## 遇到的问题与解决

### 问题 1：D2 图表不渲染

Quartz 原生支持 Mermaid，但不支持 D2 图表语言。

**解决方案**：在 GitHub Actions 中预编译 D2 为 SVG

```yaml
- name: Install D2
  run: curl -fsSL https://d2lang.com/install.sh | sh -s --

- name: Compile D2 diagrams
  run: |
    find content -name "*.md" | while read file; do
      dir=$(dirname "$file")
      # 提取 d2 代码块并编译
      perl -0777 -ne '
        while (/```d2[^\n]*\n(.*?)```/gs) {
          print $1;
        }
      ' "$file" | while IFS= read -r block; do
        if [ -n "$block" ]; then
          hash=$(echo "$block" | md5sum | cut -c1-8)
          svg_name="${hash}.svg"
          echo "$block" | d2 - "$dir/assets/$svg_name"
          # 替换代码块为 img 标签
          # ...
        fi
      done
    done
```

**关键点**：SVG 路径必须使用绝对路径（如 `/designs/observability/assets/xxx.svg`），否则在子目录页面中会出现 404。

### 问题 2：目录显示杂乱

Explorer 组件默认把所有文件平铺显示，层级不清晰。

**解决方案**：配置 filterFn、mapFn、sortFn

```typescript
// quartz.layout.ts
const explorerConfig = {
  title: "目录",
  folderDefaultState: "collapsed",
  // 隐藏 _MOC 索引文件
  filterFn: (node) => !node.name.startsWith("_"),
  // 简化文件名 + 文件夹中文化
  mapFn: (node) => {
    if (node.name.match(/^ADR-\d{3}/)) {
      node.displayName = node.name.replace(/^(ADR-\d{3})-.+$/, "$1")
    }
    const folderNames = {
      "decisions": "📋 架构决策",
      "designs": "🏗️ 设计文档",
      // ...
    }
    if (node.isFolder && folderNames[node.name]) {
      node.displayName = folderNames[node.name]
    }
    return node
  },
  // 文件夹优先排序
  sortFn: (a, b) => {
    if (a.isFolder && !b.isFolder) return -1
    if (!a.isFolder && b.isFolder) return 1
    return a.name.localeCompare(b.name, "zh-CN")
  },
}
```

### 问题 3：双仓库同步

文档源在 Patra-api，发布站在 Patra-docs，每次都要手动复制。

**解决方案**：Patra-api 添加自动同步工作流

```yaml
# .github/workflows/sync-docs.yml
name: Sync Docs to Patra-docs

on:
  push:
    branches: [main]
    paths:
      - 'docs/**'

jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          path: patra-api

      - uses: actions/checkout@v4
        with:
          repository: linqibin0826/Patra-docs
          token: ${{ secrets.DOCS_SYNC_TOKEN }}
          path: patra-docs

      - name: Sync docs content
        run: |
          cd patra-docs/content
          find . -mindepth 1 -maxdepth 1 ! -name 'index.md' -exec rm -rf {} +
          cp -r ../../patra-api/docs/* .
          rm -rf .obsidian

      - name: Commit and push
        run: |
          cd patra-docs
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add -A
          git diff --staged --quiet || git commit -m "docs: 同步文档" && git push
```

**配置 Token**：
1. 创建 Fine-grained PAT，仅授权 Patra-docs 的 Contents 读写权限
2. 添加到 Patra-api 的 Secrets：`DOCS_SYNC_TOKEN`

## 美化定制

### 配色方案（温暖舒适风格）

```typescript
// quartz.config.ts
colors: {
  lightMode: {
    light: "#faf9f6",      // 米白背景
    secondary: "#b45309",  // 琥珀色链接
    // ...
  },
  darkMode: {
    light: "#1c1917",      // 深石墨
    secondary: "#fbbf24",  // 金黄色链接
    // ...
  },
}
```

### 字体配置

```typescript
typography: {
  header: "Noto Serif SC",   // 思源宋体（标题）
  body: "Noto Sans SC",      // 思源黑体（正文）
  code: "JetBrains Mono",    // 代码字体
},
```

### 首页卡片布局

```markdown
<div class="homepage-cards">
  <div class="card">
    <h3>🏗️ 架构设计</h3>
    <p>系统架构、模块设计</p>
    <a href="/designs/_MOC">查看 →</a>
  </div>
  <!-- 更多卡片 -->
</div>
```

## 最终架构

```d2
direction: down

# 样式定义
classes: {
  repo: {
    shape: rectangle
    style: {
      fill: "#e8f5e9"
      stroke: "#4caf50"
      border-radius: 8
    }
  }
  action: {
    shape: rectangle
    style: {
      fill: "#fff3e0"
      stroke: "#ff9800"
      border-radius: 8
    }
  }
  output: {
    shape: rectangle
    style: {
      fill: "#e3f2fd"
      stroke: "#2196f3"
      border-radius: 8
    }
  }
}

# 节点
patra-api: "Patra-api/docs\n(Obsidian 编辑)" {
  class: repo
}

sync-workflow: "GitHub Actions\nsync-docs.yml" {
  class: action
}

patra-docs: "Patra-docs/content" {
  class: repo
}

deploy-workflow: "GitHub Actions\ndeploy.yml" {
  class: action
}

steps: "D2 编译 → Quartz 构建" {
  shape: text
  style.font-size: 12
}

github-pages: "GitHub Pages" {
  class: output
}

site: "linqibin0826.github.io/Patra-docs" {
  shape: text
  style.font-size: 14
  style.bold: true
}

# 连接
patra-api -> sync-workflow: "git push\n(docs/ 变更)" {
  style.stroke: "#666"
}
sync-workflow -> patra-docs: "复制文件" {
  style.stroke: "#666"
}
patra-docs -> deploy-workflow: "自动触发" {
  style.stroke: "#666"
}
deploy-workflow -> steps
steps -> github-pages
github-pages -> site
```

## 参考资料

- [Quartz 官方文档](https://quartz.jzhao.xyz/)
- [D2 语言文档](https://d2lang.com/)
- [GitHub Pages 文档](https://docs.github.com/en/pages)
