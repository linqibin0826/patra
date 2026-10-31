// 真实内容防线：content/ 下的全部条目、路线、主题、标签跑全部规则，必须零问题。
// 写内容后跑 pnpm test，按列出的问题修。
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { readContent, repoFileExists } from "./load";
import type { ContentIssue } from "./types";
import { validateContent } from "./validate";

const learnRoot = join(import.meta.dirname, "../../..");

function format(issues: ContentIssue[]) {
  return `内容校验发现 ${issues.length} 个问题：\n${issues
    .map((i) => `  [${i.rule}] ${i.file}：${i.message}`)
    .join("\n")}`;
}

describe("真实内容", () => {
  const raw = readContent(learnRoot);

  it("只读条目、路线、主题与标签表", () => {
    const files = [...raw.entries, ...raw.routes, ...raw.topics, ...(raw.tags ? [raw.tags] : [])];
    for (const { file } of files) {
      expect(file).toMatch(
        /^content\/(entries\/[^/]+\.mdx|routes\/[^/]+\.yaml|topics\/[^/]+\.yaml|tags\.yaml)$/,
      );
    }
  });

  it("全部规则零问题", () => {
    const { issues } = validateContent(raw, { fileExists: repoFileExists(learnRoot) });
    expect(issues, format(issues)).toEqual([]);
  });
});
