# Claude Design 快照

> **来源**：[Claude Design](https://claude.ai/design)（claude.ai 内置设计工具）；v0.7 起改用 Claude Code 内的 Design 画布（Artifact），快照为画布源文件而非 zip 导出
> **策略**：决策 A · Prompt 直发主导 + zip 快照备份（见 `docs/patra/release-specs/v0.4-portal-foundation.md`）

## 用途

- **日常工作流**：实现 Issue 时，用 Claude Design 的 "Send to local coding agent" 模式生成 prompt + anthropic.com URL；粘到 Claude Code 后由 WebFetch 实时拉取设计文件并实现。
- **里程碑备份**：Design 类 Issue 定稿时（design system、首页 hi-fi、后续重要 hi-fi 稿）各下载一次 zip 快照入此目录。

## 用途分工

| 场景 | 来源 |
|---|---|
| 日常实现 Issue（FE 落地） | Linear 评论里的 Claude Design 持久 URL（WebFetch 实时拉取） |
| 设计资产 diff / 审计 / 断网可用 | 此目录下的 zip 快照 |
| 关键决策追溯（哪一版定的什么） | git log + zip 文件名日期 |

## 命名规则

```text
docs/patra/design/snapshots/<YYYY-MM-DD>-<topic>.zip
```

- `<YYYY-MM-DD>`：下载日期（不是发布日期）
- `<topic>`：kebab-case 主题名，如 `design-system`、`homepage-hifi`、`search-results-hifi`

## 现有快照

| 文件 | 关联 Issue | 关联 Project | 内容 |
|---|---|---|---|
| `2026-05-23-design-system.zip` | [PAP-26](https://linear.app/papertrace/issue/PAP-26) | v0.4 Portal Foundation | Patra DS — 色板（paper / ink / clay / teal + 语义 moss/amber/rust/slate）+ 字体（Inter / Source Serif 4 / JetBrains Mono）+ 4px spacing scale + radius/shadows/motion tokens + 6 类核心组件（buttons / cards / inputs / chips-badges / table / menu-palette） |
| `2026-05-23-homepage-hifi.zip` | [PAP-29](https://linear.app/papertrace/issue/PAP-29) | v0.4 Portal Foundation | 首页 hi-fi 可运行原型（HTML/CSS/JS · 桌面 + 移动同源响应式）— 6 区块（TopNav / Hero / TopicCloud / Journals / ExploreFeed / Footer）+ 880/720 双断点 + clay AI 速读 + Tweaks 调参面板 6 项 + 品牌 SVG 三件套；作为 [PAP-32](https://linear.app/papertrace/issue/PAP-32) 像素级复刻到 Next.js 15 + Tailwind v4 + shadcn/ui 的输入 |
| `2026-06-06-detail-pages-hifi.zip` | [PAP-44](https://linear.app/papertrace/issue/PAP-44) | v0.5 Portal Detail Pages | 期刊详情 / 文献详情 / 期刊列表 hi-fi（HTML/CSS/JS 原型）— `detail-pages.jsx`（JournalDetail + PaperDetail 编排）+ `detail-shared.jsx`（Disclosure / Metric / IdentifierChip / RatingTable / TrendChart / skeleton 等共享基元）+ `detail.css` + `status-screens.jsx`（404 / error 状态屏）+ `patra-ds-cards/` 组件卡（disclosure / identifier-chip / skeleton / status-screens / evidence-badge）+ 64 张截图；作为 [PAP-44](https://linear.app/papertrace/issue/PAP-44) 期刊详情页像素级复刻输入（文献详情留 PAP-45、期刊列表留 PAP-47）。已剔除 22M / 1.3M 的 standalone HTML 导出（git 体积）|
| `2026-09-24-papers-list-hifi.zip` | [PAP-51](https://linear.app/papertrace/issue/PAP-51) | v0.7 Papers Search | 文献浏览/检索页 `/papers` hi-fi（Design 画布源文件 `.dc.html` × 7 + `canvas.json` + 生成脚本 `gen.py`）— 桌面 1440 三态（浏览 / 检索 / 无结果）+ 移动 390 四态（浏览 / 检索 / 无结果 / 筛选抽屉）；骨架复刻 `/journals` 页组件，文献卡为首页 PaperCard 紧凑列表变体；持久画布 https://claude.ai/artifact/33bSUxYM2rff1wyhTg2wes。作为 [PAP-53](https://linear.app/papertrace/issue/PAP-53) 文献页像素级复刻输入 |

## 解压与查看

```bash
# 解压
unzip docs/patra/design/snapshots/2026-05-23-design-system.zip -d /tmp/patra-ds

# 查看入口
open /tmp/patra-ds/patra-ds/README.md

# 在浏览器查看 preview 卡片
open /tmp/patra-ds/patra-ds/project/preview/comp-buttons.html
```

> **提示**：实现 FE Issue（如 PAP-30 tokens 落地）时优先用 Linear 评论里贴的 Claude Design 持久 URL；zip 用于 git 内备份与离线参考。
