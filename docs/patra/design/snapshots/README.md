# Claude Design 快照

> **来源**：[Claude Design](https://claude.ai/design)（claude.ai 内置设计工具）；v0.7 起改用 Claude Code 内的 Design 画布（Artifact），快照为画布源文件而非 zip 导出
> **策略**：决策 A · Prompt 直发主导 + zip 快照备份（见 `docs/patra/release-specs/v0.4-portal-foundation.md`）
>
> **当前设计系统**：[Patra Design System](https://claude.ai/artifact/H6ghni1vGUZen5cZubg1R9)（2026-09 起，以 portal 代码为准，含 token、组件规范与用法）。下方 `2026-05-23-design-system.zip` 是 v0.4 时期旧版 Claude Design 的设计系统，仅作历史存档，其中「内部管理后台」定位、teal 发现色、侧栏布局等已不再适用。

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
| `2026-05-23-design-system.zip` | [PAP-26](https://linear.app/papertrace/issue/PAP-26) | v0.4 Portal Foundation | 【历史存档，已被 [Patra Design System](https://claude.ai/artifact/H6ghni1vGUZen5cZubg1R9) 取代】Patra DS — 色板（paper / ink / clay / teal + 语义 moss/amber/rust/slate）+ 字体（Inter / Source Serif 4 / JetBrains Mono）+ 4px spacing scale + radius/shadows/motion tokens + 6 类核心组件（buttons / cards / inputs / chips-badges / table / menu-palette） |
| `2026-05-23-homepage-hifi.zip` | [PAP-29](https://linear.app/papertrace/issue/PAP-29) | v0.4 Portal Foundation | 首页 hi-fi 可运行原型（HTML/CSS/JS · 桌面 + 移动同源响应式）— 6 区块（TopNav / Hero / TopicCloud / Journals / ExploreFeed / Footer）+ 880/720 双断点 + clay AI 速读 + Tweaks 调参面板 6 项 + 品牌 SVG 三件套；作为 [PAP-32](https://linear.app/papertrace/issue/PAP-32) 像素级复刻到 Next.js 15 + Tailwind v4 + shadcn/ui 的输入 |
| `2026-06-06-detail-pages-hifi.zip` | [PAP-44](https://linear.app/papertrace/issue/PAP-44) | v0.5 Portal Detail Pages | 期刊详情 / 文献详情 / 期刊列表 hi-fi（HTML/CSS/JS 原型）— `detail-pages.jsx`（JournalDetail + PaperDetail 编排）+ `detail-shared.jsx`（Disclosure / Metric / IdentifierChip / RatingTable / TrendChart / skeleton 等共享基元）+ `detail.css` + `status-screens.jsx`（404 / error 状态屏）+ `patra-ds-cards/` 组件卡（disclosure / identifier-chip / skeleton / status-screens / evidence-badge）+ 64 张截图；作为 [PAP-44](https://linear.app/papertrace/issue/PAP-44) 期刊详情页像素级复刻输入（文献详情留 PAP-45、期刊列表留 PAP-47）。已剔除 22M / 1.3M 的 standalone HTML 导出（git 体积）|
| `2026-09-24-papers-list-hifi.zip` | [PAP-51](https://linear.app/papertrace/issue/PAP-51) | v0.7 Papers Search | 文献浏览/检索页 `/papers` hi-fi（Design 画布源文件 `.dc.html` × 7 + `canvas.json` + 生成脚本 `gen.py`）— 桌面 1440 三态（浏览 / 检索 / 无结果）+ 移动 390 四态（浏览 / 检索 / 无结果 / 筛选抽屉）；骨架复刻 `/journals` 页组件，文献卡为首页 PaperCard 紧凑列表变体；持久画布 https://claude.ai/artifact/33bSUxYM2rff1wyhTg2wes。作为 [PAP-53](https://linear.app/papertrace/issue/PAP-53) 文献页像素级复刻输入 |
| `2026-10-04-accounts-hifi.zip` | [PAP-61](https://linear.app/papertrace/issue/PAP-61) | v0.8 Accounts | 登录页 `/login` / 注册页 `/register` / 导航栏账号区 hi-fi（Design 画布源文件 `.dc.html` × 51 + `canvas.json` + `patra.css` + 生成脚本 `gen.py` + 品牌标记与叶脉素材）— 桌面 1440×900 与移动 375×812 各一套：登录 8 态、注册 8 态、导航栏桌面 8 块 / 移动 9 块（未登录 / 占位 / 圆形标识 / 用户菜单 / 长邮箱截断 / 退出进行中 / 三条成功提示），另有「表单组件」「导航栏账号区」两块组件规范；专注布局为纸墨分栏（左表单、右墨色品牌面板）；`patra.css` 上半段取自 portal 的 `tokens.css`，下半段是新增组件的状态样式，每条规则注释里有对应的 Tailwind 写法；持久画布 https://claude.ai/artifact/8jupGAB2phFjwVEBVgQHHX。作为 [PAP-68](https://linear.app/papertrace/issue/PAP-68) 登录 / 注册页与用户菜单落地的输入 |
| `2026-10-19-fulltext-reader-hifi.zip` | [PAP-83](https://linear.app/papertrace/issue/PAP-83) | v0.11 Full Text Reader | 文献详情页 `/papers/[id]` 全文阅读的可交互画板（Design 画布源文件：`Main.dc.html` 桌面 1440 阅读页本体 + `Mobile.dc.html` 移动 375 引用 Main 打开窄屏模式 + `canvas.json` + `patra.css`）— 场景用 Tweaks 切换：有全文 / 有全文无 PDF / 无全文（三种原因）/ 有 PDF 无正文 / 长表格 + MathML 公式压力样例 / 全文加载中 / 加载失败，另有「作者手稿」开关；Play 里可点引用跳参考文献再回正文、目录、长表展开、图片放大、移动目录面板。素材是 PMC 公开桶里的真实开放获取文章（样例集 `data/fulltext-samples.json` 与抽取脚本 `scripts/`）；`harness/` 是本机自查用的最小运行时垫片与 Playwright 截图脚本。持久画布 https://claude.ai/artifact/4GKkd2ioquYvJmxP3vXjED。作为 [PAP-88](https://linear.app/papertrace/issue/PAP-88) 详情页全文阅读落地的输入 |
| `2026-10-20-paper-detail-layout-hifi.zip` | [PAP-89](https://linear.app/papertrace/issue/PAP-89) | （不挂版本） | 文献详情页排版重设计（Design 画布源文件：`Main.dc.html` 首屏 · 页头与摘要 + `Reading.dc.html` 正文 · 章节、引用、图表 + `Backmatter.dc.html` 文末 · 声明、参考文献、档案 + `Titles.dc.html` 长标题规则 + `Figures.dc.html` 插图宽度规则 + `canvas.json`），均为桌面 1440 页面；画板上的方括号是占位、「图 1」「表 10」是中文示意（落地照原文标签，见工程 spec §5.9）。持久画布 https://claude.ai/artifact/PwmifxFVFZwrEmq1E5WUBJ。作为 [PAP-89](https://linear.app/papertrace/issue/PAP-89) 落地的输入，工程 spec `docs/patra/specs/2026-10-20-paper-detail-layout-design.md` |

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
