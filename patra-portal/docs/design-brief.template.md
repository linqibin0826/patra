# Claude Design 交接简报（Design Brief）

> 每次迭代：把"和 Claude Code 聊定的产品内容"压成本文件的一份副本，交给 Claude Design。
> 设计系统与代码库它已 onboard 过（首页就是它做的），**每次只交增量**。

## 它在流程里的位置

```
①你 + Claude Code        ②Claude Code 产出        ③交给 Claude Design        ④Design 产出          ⑤回 Claude Code
聊定本次迭代内容   ───►  这份「设计简报」副本  ───►  简报 + 真实数据 + 参考  ───►  原型(HTML/React)  ───►  按 TDD 落地
(brainstorming/spec)                                  (设计系统/代码库已 onboard)   Export→Handoff
```

## 三条铁律（来自业界踩坑）

1. **不在同一对话里既设计又写生产代码** —— Claude Design 只做视觉探索；落地交回 Claude Code。
2. **不写"现代 / 简洁 / clean"这种空词** —— 它会自行选默认值，多半偏离你脑子里的样子。要点名具体。
3. **不漏状态** —— 空 / 加载 / 错误 / 无结果，不逐屏点名它就不画。

## 怎么用

1. 复制下面「— 模板 —」整段到 `docs/design-briefs/YYYY-MM-DD-<slug>.md`，填满。
2. 在 Claude Design 里把填好的正文贴进 chat（首条 prompt）。
3. 附上：相关现有页的 **dev URL**（让它用 web capture 抓真实页面）+ **真实数据样例**。
4. 迭代用 **inline 注释**（局部：间距/按钮/组件替换）/ **chat**（结构、布局方向、大的视觉调整）。
5. **Export → Handoff to Claude Code** → 交回本仓库，Claude Code 按 TDD 落地。

## 不要写进简报的

- ❌ 设计系统（色 / 字 / 间距 / 阴影 / 圆角）—— 已从 `src/styles/tokens.css` onboard，重复反而让它漂移。
- ❌ 整个代码库 —— 已链接，按组件名引用即可。
- ❌ 实现细节（用哪个 hook、状态怎么管、RSC vs client）—— 那是第 ⑤ 步 Claude Code 的活。

---

# — 模板（复制下面整段）—

# [迭代名] — Design Brief

**目标**：<一句话，这版让用户能做什么>
**关联**：<对应 spec / plan 链接，可空>

## 1. 用户与场景

<谁 · 在什么情境下 · 要完成什么任务>（例：临床医生在查房间隙，想快速核对一篇文献的结论与证据等级）

## 2. 要设计的屏 / 流程

| 屏 | 路由 | 用途 | 入口 → 出口 |
|----|------|------|------------|
| <屏名> | `/<path>` | <一句话> | <从哪来 → 到哪去> |

> 多屏流程补一行文字流：A →(动作) B →(动作) C

## 3. 每屏内容与数据 ← 最关键，越具体越好

**<屏名>**
- 信息块与层级：<标题 / 作者 / 期刊 / 年份 / AI 摘要徽标 / …，按视觉重要性排序>
- 关键动作：<排序 / 筛选 / 分页 / 收藏 / …>
- 真实数据样例（贴一两条真实 DTO，别用 lorem ipsum）：
  ```json
  { "...": "..." }
  ```
- 信息密度：<高 / 中 —— 参照现有紧凑字阶，13.5px base>

## 4. 必做状态（逐屏点名）

- [ ] 默认（有数据）
- [ ] 加载中（沿用现有 `explore-feed/skeleton` 约定）
- [ ] 空 / 无结果（沿用 `explore-feed/empty` 风格）
- [ ] 错误
- [ ] 分页 / 加载更多（如适用）
- **边界**：超长标题截断、缺失字段（无摘要 / 无封面）、慢网络、超长列表

## 5. 复用 vs 新增组件

- **复用**（按名引用，让它别重造）：`TopNav` `Hero` `PaperCard` `JournalCoverCard` `TopicCloud` `Composer` `AISummaryBadge` `explore-feed/*`，原语 `button` `card` `input` `badge` `sheet` `tabs`
- **新增**：<描述需要的新组件及用途，例：`FilterRail` —— 左侧筛选栏>

## 6. 交互与响应式

- 元素状态：hover / active / focus / disabled
- 微交互 / 动效：<过渡、骨架→内容、展开收起>
- 响应式：<桌面布局 → 移动端如何 stack；关键断点>

## 7. 约束

- 视觉：沿用现有暖纸感 editorial 风格 + 高信息密度（borders 做活、阴影克制）
- 技术：产出需能映射到 **Next 16 App Router（RSC 优先）/ Tailwind v4 / base-ui(shadcn)**
- 可访问性：语义化 HTML + ARIA + 键盘可达 + 合理 focus order
- 暗色模式：<要 / 不要>

## 8. 参考

- 现有相关页：<dev URL（让它 web capture）或截图>
- 竞品 / 灵感：<截图 + 我要它的"什么"，例：PubMed 结果页的信息密度、Semantic Scholar 的 TLDR 卡片>

## 9. Done 判定

- 覆盖：<这些屏> × <这些状态> × <响应式> 全部产出
- 交回：Export → Handoff to Claude Code

---

# — 填写示例（节选：检索结果页）—

> 仅示范字段 2–4 的填法，帮你建立"够具体"的手感。

## 2. 要设计的屏 / 流程

| 屏 | 路由 | 用途 | 入口 → 出口 |
|----|------|------|------------|
| 检索结果 | `/search?q=` | 展示一次检索的命中文献列表，支持排序/筛选 | 首页搜索框提交 → 点条目进 `/paper/[id]` |

## 3. 每屏内容与数据

**检索结果**
- 信息块与层级：① 顶部检索摘要条（命中数 + 当前 query + 排序/筛选入口）→ ② 结果列表，每条复用 `PaperCard`：标题（最重要）> 作者 > 期刊·年份 > AI 摘要徽标 `AISummaryBadge` > 证据等级标签
- 关键动作：按相关度/时间排序、按年份/期刊/类型筛选、分页
- 真实数据样例：
  ```json
  {
    "total": 1284,
    "items": [
      {
        "id": "PMID-39912345",
        "title": "Tirzepatide for the Treatment of Obstructive Sleep Apnea",
        "authors": ["Malhotra A", "Grunstein RR", "et al."],
        "journal": "N Engl J Med",
        "year": 2024,
        "aiSummary": true,
        "evidenceLevel": "RCT"
      }
    ]
  }
  ```
- 信息密度：高 —— 一屏尽量多放结果，参照现有 explore-feed 的紧凑卡片。

## 4. 必做状态

- [x] 默认（有结果列表）
- [x] 加载中（skeleton 卡片列表）
- [x] 无结果（"未找到 'xxx' 的相关文献" + 检索建议）
- [x] 错误（检索服务不可用）
- [x] 分页（底部 load more / 页码）
- 边界：超长标题两行截断省略；缺 `aiSummary` 时不显徽标；`evidenceLevel` 缺失时不占位。
