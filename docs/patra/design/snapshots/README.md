# Claude Design 快照

> **来源**：[Claude Design](https://claude.ai/design)（claude.ai 内置设计工具）
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

```
docs/patra/design/snapshots/<YYYY-MM-DD>-<topic>.zip
```

- `<YYYY-MM-DD>`：下载日期（不是发布日期）
- `<topic>`：snake-case 主题名，如 `design-system`、`homepage-hifi`、`search-results-hifi`

## 现有快照

| 文件 | 关联 Issue | 关联 Project | 内容 |
|---|---|---|---|
| `2026-05-23-design-system.zip` | [PAP-26](https://linear.app/papertrace/issue/PAP-26) | v0.4 Portal Foundation | Patra DS — 色板（paper / ink / clay / teal + 语义 moss/amber/rust/slate）+ 字体（Inter / Source Serif 4 / JetBrains Mono）+ 4px spacing scale + radius/shadows/motion tokens + 6 类核心组件（buttons / cards / inputs / chips-badges / table / menu-palette） |

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
