---
name: create-issue
description: 从对话内容批量创建 GitHub Issue 到当前仓库，专为独立开发者设计。触发：用户说"建 issue / 帮我建 / 记一下 / 加进 backlog / 跟踪一下"，或描述 bug、提出 feature 想法后要求记录，或说"先放着 / 还不确定"（→ idea 标签）。涉及代码任务的记录与跟踪都应使用此技能。用 gh CLI，强制 `-F file.md`（避免 shell 多行转义）。
---

# 从对话创建 GitHub Issue（Solo Dev 版）

为独立开发者设计：在对话里讨论了 bug、feature、想法，由 agent 批量整理成 GitHub issue，本人不动手。

## 为什么这么设计

- **批量 > 实时**：每聊一句建一个 issue 会产生噪音和低质量条目。对话末尾统一归档，质量更高。
- **文件 > 内联**：`gh issue create -b "多行字符串"` 在 shell 里转义经常出错（引号、反引号、$、换行）。用 `-F /tmp/xxx.md` 把正文写到文件后引用，agent 失败率会显著降低。
- **4 个 label 够用**：solo 项目不需要 20 个 label。bug / feat / chore / idea 已经覆盖 95% 场景。
- **idea 标签是关键**：很多想法只是"先记下来"，还没决定做不做。塞进 feat 会污染 backlog。给它单独一个 label。

---

## 何时使用

**优先用此技能的情形**：
- 用户明确说 "建 issue / 帮我建 / 入个库 / 加进 backlog / 提一个"
- 对话末尾用户希望把刚才讨论的多个待办归档
- 用户描述 bug 现象（"xxx 报错了 / xxx 不工作了"），要求记录或跟踪
- 用户提出 feature 想法、改进点、技术债，要求记录
- 用户说"这个想法记一下 / 先放着"——倾向 **idea** 标签

**不要用此技能的情形**：
- 用户只是在讨论，没有"记录 / 入库 / 跟踪"的意图
- 用户在问"怎么写 issue / issue 长什么样"——这是讲解问题，不是创建
- 用户在做实际编码，issue 的事可以稍后处理
- 用户的需求是查询 / 列出 / 关闭已有 issue——直接用 `gh issue list/view/close` 即可，不需要本技能

---

## 4 个 Label 体系

| Label | 用途 | 典型例子 |
|---|---|---|
| `bug` | 修复已有问题 | "登录页 SSO 模式下崩溃"、"导出 CSV 中文乱码" |
| `feat` | 新功能 | "增加文章自动保存草稿"、"接口加分页参数" |
| `chore` | 重构、技术债、依赖升级、构建 | "把 Express 迁到 Hono"、"升级 Spring Boot 到 3.4" |
| `idea` | 探索性想法，**未决定做不做** | "考虑加 AI 摘要"、"也许引入插件机制" |

**判断规则**：
- 现象描述清楚、有复现 → `bug`
- 已经想好要做、改动点明确 → `feat`
- 不改用户可见行为，只调内部 → `chore`
- 还没决定要做、需要先评估 → `idea`

判不准的优先放 `idea`，未来分诊时再升级。

---

## Issue 标题规则

- **动词开头**，描述"做什么"
- 长度 30-50 字（中文）/ 50-70 字符（英文），列表里一行能看完
- 具体到 **3 个月后我自己能看懂**，不要含糊词
- **不要前缀**（`[BUG]:` `feat:` 等），分类靠 label，标题就是标题

**对比**：

| 不好 | 好 |
|---|---|
| 登录有问题 | 修复邮箱含 `+` 号时登录报 500 |
| 加搜索 | 文章列表页增加按标题模糊搜索 |
| 优化 | 把首页接口 P95 从 800ms 降到 200ms 以内 |
| `[BUG] fix something` | 修复头像上传 >5MB 时前端白屏无提示 |

---

## Issue 正文模板

### 通用结构（所有类型适用）

```markdown
## 背景
为什么要做（1-3 句话，写清楚动机和约束）

## 验收标准
- [ ] 可勾选、可验证的条件
- [ ] 另一个条件

## 不做（可选）
- ❌ 划清边界，避免范围蔓延
```

### Bug 类额外加

```markdown
## 复现步骤
1. 第一步
2. 第二步
3. 看到的现象

## 环境（必要时）
- 版本 / 平台 / 浏览器

## 怀疑原因（可选）
快速记一下当下的猜想，便于后续排查
```

### feat 类的"轻量法"

如果是小功能、标题已经把意图说清楚了，**正文允许留空**。例如：

- 标题：`日志输出加 traceId 字段`
- 正文：空

solo 场景下不要为了"格式正确"硬填模板，标题清楚比模板齐整更重要。

### idea 类的"待决策法"

```markdown
## 想法
一句话描述想做什么

## 待评估
- [ ] 价值：解决谁的什么问题
- [ ] 成本：大概多少工作量
- [ ] 风险：可能踩什么坑

## 决策点
评估清楚前**不开工**。决定做之后转 feat 或拆成具体 issue。
```

---

## 执行流程

### 前置检查（每次都做）

1. **确认仓库上下文**——`gh repo view --json nameWithOwner -q .nameWithOwner` 看当前目录解析到哪个仓库，确保 issue 建到对的项目
2. **确认登录态**——若 `gh auth status` 失败，先告诉用户去 `gh auth login`
3. **查重**——`gh issue list --search "<关键词>" --state all --limit 5`，避免重复入库；如果用户给的是一批，对每条用核心关键词查一次

### 创建单个 issue

```bash
# 1. 确保临时目录存在
mkdir -p /tmp/patra

# 2. 写正文到临时文件（用 Write 工具写，不要 echo/printf）
#    路径：/tmp/patra/issue-<类型>-<时间戳>.md
#    示例：/tmp/patra/issue-bug-20260516-173242.md

# 3. 用 gh issue create 提交
#    必须单行命令，不允许反斜杠换行
#    必须用 -F file，禁止 -b "inline"
gh issue create -t "修复邮箱含+号时登录报500" -F /tmp/patra/issue-bug-20260516-173242.md -l "bug" -a @me
```

**关键细节**：
- 时间戳用 `$(date +%Y%m%d-%H%M%S)`
- 标题里如果有英文双引号，用单引号包标题；如果有单引号，转义或重写标题
- 多个 label 用 `-l "bug" -l "high-priority"`，每个 label 一个 `-l` 参数
- 不指定 `--repo`，默认建到当前仓库（前置检查已确认）

### 批量创建多个 issue

1. 先把所有候选**列清单**给用户确认（每条 1 行：标题 + label）
2. 等用户 confirm 后再执行
3. 每个 issue 一次 gh 命令调用，按顺序执行
4. **一次最多 10 个**，超过的话强制分批，告诉用户"剩余 N 个先 hold，处理完这批再继续"

### 完成后

每建一个 issue，立即报告给用户：
```
✅ #42 - 修复邮箱含+号时登录报500
   https://github.com/<owner>/<repo>/issues/42
```

批量完成后，给一个汇总，标记哪些成功、哪些失败、哪些跳过（查重命中）。

---

## 批量与时机

**收集阶段（对话进行中）**：
- 不要每条消息都建 issue
- 心里记下候选条目（这是 bug、那是想法）
- 用户继续聊就先不动

**触发阶段（什么时候真正建）**：
- 用户**明确要求**："把刚才提到的建成 issue"
- 对话有明显**收尾迹象**时主动询问："我们刚才讨论了 X 个待处理事项，要不要建成 issue 跟踪？"

**确认阶段**：
- 列清单 → 用户 OK → 执行
- 不要列完直接建，留 confirm 这一步

---

## 反模式（避免这样做）

| 反模式 | 问题 | 怎么做 |
|---|---|---|
| `gh issue create -b "多行字符串"` | shell 转义易出错 | 用 `-F /tmp/xxx.md` |
| `gh issue create -t "..." \`<换行> | 反斜杠换行 + 多参数，常被 shell 吃掉 | 命令必须单行 |
| 每条消息都建 issue | 噪音、低质量、标题模糊 | 收集 → 末尾批建 |
| 一个 issue 列 20 个改动点 | 永远关不掉、PR 说不清 | 拆成多个 issue |
| 标题用问句或模糊词（"修一下"、"看看 xxx"） | 三个月后不知道在说啥 | 动词 + 具体 |
| 把"愿望"开成 feat | 污染 backlog | 用 `idea` 或建议开 GitHub Discussion |
| 不查重直接建 | 重复 issue | 先 `gh issue list --search` |
| 不报告 issue URL | 用户不知道建在哪 | 每建一个立刻给编号 + URL |

---

## 首次使用前置（仅一次）

如果项目还没建过这 4 个 label，先创建：

```bash
gh label create bug --color "d73a4a" --description "修复已有问题" --force
gh label create feat --color "0e8a16" --description "新功能" --force
gh label create chore --color "c5def5" --description "重构/技术债/构建/依赖" --force
gh label create idea --color "fbca04" --description "探索性想法，未决定做不做" --force
```

执行前先 `gh label list` 看哪些已存在，避免无意义重复。

---

## 完成检查清单

每次任务结束前自检：

- [ ] 仓库上下文确认过（建到了对的 repo）
- [ ] 所有候选都做过查重
- [ ] 每个 issue 都用了 `-F` 引用文件，没有 `-b` 内联
- [ ] 每个命令都是单行，没有反斜杠换行
- [ ] 每个 issue 都报告了编号和 URL
- [ ] 单次未超过 10 个；超过的话明确告诉用户分批
- [ ] 跳过的（查重命中、用户取消）有明确告知

---

## 参考资料

灵感来源：
- johnlindquist GitHub Tasks Output Style for Claude Code（`-F file` 模式、查重、批量）
- Six Feet Up: Automating GitHub Issue Creation with Claude Code（会议记录批建工作流）
- GitHub awesome-copilot `github-issues` skill（标题与模板规范的反向参考——本技能刻意做了简化）
