// patra-learn/src/lib/content/code-refs.ts
// 代码引用（决策 F）：`路径@提交SHA` 或 `路径@pending`。测试只查文件在当前工作树里在不在；
// 钉住之后有没有改过由体检报告（report.ts）用 git 查
import type { CodeRefUse } from "./mdx-rules";
import type { ContentIssue } from "./types";

export const GITHUB_REPO_URL = "https://github.com/linqibin0826/patra";

export interface ParsedCodeRef {
  /// 相对仓库根
  path: string;
  /// 40 位小写 SHA，或还没钉时的 pending
  sha: string;
}

const SHA_PATTERN = /^[0-9a-f]{40}$/;
const LINES_PATTERN = /^\d+(-\d+)?$/;

/// 解析 at 属性；写法不对返回 null
export function parseCodeRefAt(at: string): ParsedCodeRef | null {
  const index = at.lastIndexOf("@");
  if (index <= 0) return null;
  const path = at.slice(0, index);
  const sha = at.slice(index + 1);
  if (!SHA_PATTERN.test(sha) && sha !== "pending") return null;
  if (/\s/.test(path) || /^(\/|\.\.?\/)/.test(path)) return null;
  return { path, sha };
}

/// 检查写法与文件是否还在当前工作树里
export function checkCodeRefs(
  refs: CodeRefUse[],
  fileExists: (repoPath: string) => boolean,
): ContentIssue[] {
  const issues: ContentIssue[] = [];
  for (const ref of refs) {
    const parsed = parseCodeRefAt(ref.at);
    if (!parsed) {
      issues.push({
        rule: "code-ref-invalid",
        file: ref.file,
        message: `代码引用 ${ref.at} 写法不对：写成 路径@40位提交SHA（路径相对仓库根），代码还没进 main 时写 路径@pending`,
      });
      continue;
    }
    if (ref.lines !== undefined && !LINES_PATTERN.test(ref.lines)) {
      issues.push({
        rule: "code-ref-invalid",
        file: ref.file,
        message: `代码引用 ${parsed.path} 的 lines="${ref.lines}" 写法不对：写成 12 或 12-30`,
      });
      continue;
    }
    if (!fileExists(parsed.path)) {
      issues.push({
        rule: "code-ref-missing",
        file: ref.file,
        message: `代码引用的文件 ${parsed.path} 在仓库里已经不存在：找到新位置重新钉，或改写这段`,
      });
    }
  }
  return issues;
}

/// GitHub 永久链接；pending 没有链接
export function codeRefUrl(ref: ParsedCodeRef, lines?: string): string | null {
  if (ref.sha === "pending") return null;
  const base = `${GITHUB_REPO_URL}/blob/${ref.sha}/${ref.path}`;
  if (!lines) return base;
  const [from, to] = lines.split("-");
  return to ? `${base}#L${from}-L${to}` : `${base}#L${from}`;
}
