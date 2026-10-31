// patra-learn/src/lib/content/report.ts
// 体检报告：全部校验问题 + 每个代码引用钉住之后的状态。维护技能 patra:learn 的体检只读这份报告，
// 格式见工程 spec 第 8 节。git 经 GitPort 注入，真实实现在 scripts/content-report.ts
import { type ParsedCodeRef, parseCodeRefAt } from "./code-refs";
import type { CodeRefUse } from "./mdx-rules";
import type { ContentIssue } from "./types";

export interface GitPort {
  head(): string;
  hasCommit(sha: string): boolean;
  /// rev 为提交 SHA 或 HEAD
  hasPath(rev: string, path: string): boolean;
  /// sha..HEAD 之间改过 path 的提交，新到旧
  commitsTouching(sha: string, path: string): string[];
}

export type CodeRefStatus =
  | "unchanged"
  | "changed"
  | "missing"
  | "not-in-commit"
  | "unknown-sha"
  | "pending";

export interface CodeRefReport {
  entry: string;
  path: string;
  sha: string;
  lines?: string;
  status: CodeRefStatus;
  /// 只在 changed / unchanged 时有
  commitsSince?: number;
  lastChangedBy?: string;
}

export interface HealthReport {
  generatedAt: string;
  head: string;
  issues: ContentIssue[];
  codeRefs: CodeRefReport[];
}

/// 判断一个引用钉住之后的状态
export function codeRefStatus(
  ref: ParsedCodeRef,
  git: GitPort,
): Pick<CodeRefReport, "status" | "commitsSince" | "lastChangedBy"> {
  if (ref.sha === "pending") return { status: "pending" };
  if (!git.hasCommit(ref.sha)) return { status: "unknown-sha" };
  if (!git.hasPath(ref.sha, ref.path)) return { status: "not-in-commit" };
  if (!git.hasPath("HEAD", ref.path)) return { status: "missing" };
  const commits = git.commitsTouching(ref.sha, ref.path);
  const [latest] = commits;
  if (!latest) return { status: "unchanged", commitsSince: 0 };
  return { status: "changed", commitsSince: commits.length, lastChangedBy: latest };
}

/// 写法不对的引用不进 codeRefs，它已在 issues 里（code-ref-invalid）
export function buildReport(input: {
  issues: ContentIssue[];
  codeRefs: CodeRefUse[];
  git: GitPort;
  now: Date;
}): HealthReport {
  const codeRefs: CodeRefReport[] = [];
  for (const use of input.codeRefs) {
    const parsed = parseCodeRefAt(use.at);
    if (!parsed) continue;
    codeRefs.push({
      entry: use.entry,
      path: parsed.path,
      sha: parsed.sha,
      ...(use.lines !== undefined ? { lines: use.lines } : {}),
      ...codeRefStatus(parsed, input.git),
    });
  }
  return {
    generatedAt: input.now.toISOString(),
    head: input.git.head(),
    issues: input.issues,
    codeRefs,
  };
}
