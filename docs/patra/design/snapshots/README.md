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
| `2026-10-22-user-center-hifi.zip` | [PAP-92](https://linear.app/papertrace/issue/PAP-92) | v0.12 User Center | 用户中心可交互画板（Design 画布源文件：`canvas.json` 4 页 20 块，8 个可交互页面各一份桌面 `X.dc.html` 与移动 `X-M.dc.html`——注册与登录 `Main`、导航栏与未验证横幅 `Nav`、设置四个分区 `Profile` / `Account` / `Security` / `Privacy`、公开主页 `User`、邮件落地页与取消注销 `Landing`，另有静态板 `Avatars`（12 色板）、`Reserve`（主页留位）、`Emails` / `Emails-M`（三类安全邮件）+ `patra.css` + `ds/patra/tokens.json`）— 场景用 Tweaks 切换，状态 ID 与简报 §4 一一对应；`harness/` 是本机自查用的最小运行时垫片与 Playwright 截图脚本。持久画布 https://claude.ai/artifact/W8hmKqm6JcFdZu5FPy6MQ8。作为 [PAP-101](https://linear.app/papertrace/issue/PAP-101) / [PAP-102](https://linear.app/papertrace/issue/PAP-102) 落地的输入，简报 `patra-portal/docs/design-briefs/v0.12/user-center.md` |
| `2026-10-24-library-hifi.zip` | [PAP-107](https://linear.app/papertrace/issue/PAP-107) | v0.13 Library | 文献库与收藏可交互画板（Design 画布源文件：`canvas.json` 5 页 20 块，6 个可交互页面各一份桌面 `X.dc.html` 与移动 `X-M.dc.html`——流程 `Flow`（步骤条串起「收藏 → 建夹 → 设公开 → 他人复制 → 导出」6 步）、收藏按钮 `Save`、详情页页头 `Detail`、文献库 `Library`、公开收藏夹页 `Collection`、用户主页 `User`；组件板 `SavePop`、`Share`、`Export`、`Cite`、`Nav` / `Nav-M`；静态板 `HeaderNarrow`（窄屏操作行方案 A / B）、`Dates`（出版日期精度）+ `patra.css` + `ds/patra/tokens.json`）— 场景用 Tweaks 切换，状态 ID 与简报 §4 一一对应；`harness/` 是本机自查用的最小运行时垫片与 Playwright 截图脚本。持久画布 https://claude.ai/artifact/BoLjKpkaQhzotxdiNJdeP6。作为 [PAP-114](https://linear.app/papertrace/issue/PAP-114) / [PAP-115](https://linear.app/papertrace/issue/PAP-115) 落地的输入，简报 `patra-portal/docs/design-briefs/v0.13/library.md` |
| `2026-10-27-discussion-hifi.zip` | [PAP-116](https://linear.app/papertrace/issue/PAP-116) | v0.14 Discussion | 文献讨论、投稿经验与提醒可交互画板（Design 画布源文件：`canvas.json` 4 页 20 块——流程 `Flow`（步骤条串起「发起讨论 → 回复 → 写投稿经验 → 追问并 @ → 提醒 → 单楼视图回复」6 步）、文献页讨论区 `Paper`、经验区所在的期刊页 `Journal`、经验页 `Post`、主页「投稿经验」标签 `Profile`、提醒列表 `Inbox`、导航铃铛 `Nav`，各有桌面 `X.dc.html` 与移动 `X-M.dc.html`；组件板 `Discussion`（文献页与经验页共用的讨论区）、`Composer`（编辑器与 @ 联想）、`Md`（Markdown 渲染）、`ExpForm`（投稿经验表单）；静态板 `Markdown`（Markdown 子集对照）、`NavNarrow`（窄屏铃铛位置 B / A / C，定稿 B）+ `patra.css` + `ds/patra/tokens.json`）— 场景用 Tweaks 切换，状态 ID 与简报 §4 一一对应；`scripts/` 含 token 拼装、手机板生成与简报页生成脚本，`harness/` 是本机自查用的最小运行时垫片与 Playwright 截图脚本。持久画布 https://claude.ai/artifact/DcyaGqN7xUMiXNbK3exaBN。作为 [PAP-124](https://linear.app/papertrace/issue/PAP-124) / [PAP-125](https://linear.app/papertrace/issue/PAP-125) / [PAP-126](https://linear.app/papertrace/issue/PAP-126) 落地的输入，简报 `patra-portal/docs/design-briefs/v0.14/discussion.md` |
| `2026-10-28-notification-inbox-hifi.zip` | [PAP-127](https://linear.app/papertrace/issue/PAP-127) | （不挂版本） | 提醒页重设计静态对比板（Design 画布源文件：`canvas.json` 8 块 + `p127.css` + `patra.css`）——定稿 `DirA`（桌面）/ `DirA-M`（窄屏 375）：按「今天 / 昨天 / 更早」分组、正文当主角、未读圆点不铺浅底；定稿 `Rule3`：评论或经验一删，提醒跟着删、未读数同步减；`Main` 是现状，`DirB`（按讨论归拢）、`DirC`（带被回复的原话）、`Rule1` / `Rule2` 未采用。同一组 8 条示例提醒。持久画布 https://claude.ai/artifact/Qwn6xpQaJAkTPFnFT6Mdix。作为 PAP-127 落地的输入，工程 spec `docs/patra/specs/2026-10-28-notification-inbox-redesign-design.md` |
| `2026-10-29-helpful-hifi.zip` | [PAP-130](https://linear.app/papertrace/issue/PAP-130) | v0.15 Helpful | 有帮助与公开计数可交互画板（Design 画布源文件：`canvas.json` 5 页 18 块——有帮助按钮 `HelpfulStates`（9 种场景 × 4 处位置，附状态表）与组件 `Helpful`；文献页 `Paper`（事实条收藏、讨论两格）、期刊页经验列表 `Journal`（「最新投稿 / 最有帮助」）、经验页 `Post`、主页 `Profile`（获赞）、四处文献卡片 `Cards`，各有桌面 `X.dc.html` 与移动 `X-M.dc.html`；组件板 `Discussion`（有帮助、「最有帮助」、楼内统计）；静态板 `CardPlace`（列表卡片计数位置 A / B / C，定稿 A）；沿用 v0.14 的 `Nav` / `Composer` / `Md` / `ExpForm` + `patra.css` + `ds/patra/tokens.json`）— 场景用 Tweaks 切换，状态 ID 与简报 §4 一一对应；`scripts/` 含 token 拼装、手机板生成与简报页生成脚本，`harness/` 是本机自查用的最小运行时垫片与 Playwright 截图脚本。持久画布 https://claude.ai/artifact/GzchLSeVWW1dnjnAHVhhDi。作为 [PAP-134](https://linear.app/papertrace/issue/PAP-134) / [PAP-135](https://linear.app/papertrace/issue/PAP-135) 落地的输入，简报 `patra-portal/docs/design-briefs/v0.15/helpful.md` |
| `2026-10-30-notification-hifi.zip` | [PAP-140](https://linear.app/papertrace/issue/PAP-140) | v0.16 Notification | 通知中心可交互画板（Design 画布源文件：`canvas.json` 5 页 11 块——提醒页 `Inbox`（「有帮助」标签与「林知远 等 2 人觉得你的投稿经验有帮助」合并条目、铃铛不含有帮助、推送进来的新条目）、设置 › 通知 `Notify`（「回复我的」「@ 我的」各三档即时保存、有帮助只在站内、账号安全邮件说明）、退订落地页 `Unsub`（先确认再退订、已退订 / 已关闭全部 / 已经退订过 / 链接无效），各有桌面 `X.dc.html` 与移动 `X-M.dc.html`；提醒邮件 `Mail`（离站时逐条：单条与同一类到点多条合一）与每日汇总 `Digest`，邮件正文照 identity `layout.html` 的 table + 行内样式，桌面板 600px、`-M` 为手机邮件客户端；沿用 v0.14 的 `Nav` + `patra.css` + `ds/patra/tokens.json`）— 场景用 Tweaks 切换，状态 ID 与简报 §4 一一对应；`scripts/` 含 token 拼装、手机板生成与简报页生成脚本，`harness/` 是本机自查用的最小运行时垫片与 Playwright 截图脚本。持久画布 https://claude.ai/artifact/8Ff2pvLjosq8sfdRV4PDBP。作为 [PAP-147](https://linear.app/papertrace/issue/PAP-147) / [PAP-148](https://linear.app/papertrace/issue/PAP-148) 落地与 [PAP-146](https://linear.app/papertrace/issue/PAP-146) 邮件版式的输入，简报 `patra-portal/docs/design-briefs/v0.16/notification.md` |
| `2026-10-31-learn-hifi.zip` | [PAP-156](https://linear.app/papertrace/issue/PAP-156) | v0.17 Learn | 学习站花园方向 hi-fi 可交互画板（Design 画布源文件：`canvas.json` 4 页 21 块）— 页面 `Main`（今天读这一条 / 从这里开始）、`Route`、`Theme`（按时间演进）、`Tag`、`Search`；四类条目 `Concept` / `Debug`（个人环境）/ `Decision`（可切已被取代）/ `Pitfall`；教学组件 `C-*` 十个（诊断题、考考自己、迁移 / 复述题、掌握度、结构图、时序图、状态机、逐步动画 + 你猜会怎样、参数开关、对比切换），状态在各画板 Tweaks 里切；`Tokens`、`Nav`、学习站自有 token `learn.css`（不用门户设计系统）；`harness/` 本机自查脚本，`scripts/` 简报页模板与生成脚本。持久画布 https://claude.ai/artifact/U2Fkw5KgjBuUHYBMGvi9fi ，设计简报 `patra-learn/docs/design-briefs/v0.17/learn-site.md`。作为 PAP-157 组件样式与 PAP-162 页面实现的输入 |

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
