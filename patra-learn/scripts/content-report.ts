// patra-learn/scripts/content-report.ts
// pnpm -s content:report：向 stdout 输出体检报告 JSON（格式见工程 spec 第 8 节）。
// 必须带 -s：不带时 pnpm 会先往 stdout 打一行脚本横幅，输出就不是合法 JSON。
// 要在完整克隆里跑；issues 非空时退出码 1，代码引用的状态不影响退出码
import { execFileSync } from "node:child_process";
import { join } from "node:path";
import { readContent, repoFileExists } from "@/lib/content/load";
import { buildReport, type GitPort } from "@/lib/content/report";
import { validateContent } from "@/lib/content/validate";

const learnRoot = join(import.meta.dirname, "..");
const repoRoot = join(learnRoot, "..");

/// 跑 git，失败返回 null
function git(...args: string[]): string | null {
  try {
    return execFileSync("git", args, { cwd: repoRoot, encoding: "utf8", stdio: "pipe" }).trim();
  } catch {
    return null;
  }
}

const gitPort: GitPort = {
  head: () => git("rev-parse", "HEAD") ?? "",
  hasCommit: (sha) => git("cat-file", "-e", `${sha}^{commit}`) !== null,
  hasPath: (rev, path) => git("cat-file", "-e", `${rev}:${path}`) !== null,
  commitsTouching: (sha, path) =>
    (git("log", "--format=%H", `${sha}..HEAD`, "--", path) ?? "").split("\n").filter(Boolean),
};

const { issues, codeRefs } = validateContent(readContent(learnRoot), {
  fileExists: repoFileExists(learnRoot),
});
const report = buildReport({ issues, codeRefs, git: gitPort, now: new Date() });
process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
if (report.issues.length > 0) process.exitCode = 1;
