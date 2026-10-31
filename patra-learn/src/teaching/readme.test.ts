// 写内容的人只读 README.md：这里保证里面每个 ```mdx 示例都能过内容校验，且每个组件都有示例
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { checkEntryBody } from "@/lib/content/mdx-rules";
import { componentRegistry } from "./registry";

const readme = readFileSync(join(import.meta.dirname, "README.md"), "utf8");

/// README 里全部 ```mdx 代码块
const examples = [...readme.matchAll(/```mdx\n([\s\S]*?)```/g)].map((m) => m[1] ?? "");

const OPEN =
  '<Diagnose question="问" options={["甲", "乙"]} answer="A" ifRight="对" ifWrong="错" />';
const CLOSE = '<Recap prompt="复述" points={[{ text: "要点一" }, { text: "要点二" }]} />';

/// 示例可能只是正文中间的一段：前后补上合法的首尾，只看组件本身的问题
function check(example: string) {
  const body = `${OPEN}\n\n${example}\n\n${CLOSE}\n`;
  return checkEntryBody({ slug: "readme", file: "README.md", body }, componentRegistry).issues;
}

describe("组件说明 README", () => {
  it("有示例，且检查真的会拦住写错的组件", () => {
    expect(examples.length).toBeGreaterThan(0);
    expect(check('<Quiz title="漏了 questions" />').map((i) => i.rule)).toEqual([
      "component-props-invalid",
    ]);
  });

  it.each(examples.map((e, i) => [i + 1, e] as const))("第 %i 个 mdx 示例零问题", (_i, example) => {
    expect(check(example)).toEqual([]);
  });

  it("每个登记的组件都有至少一个 mdx 示例", () => {
    const missing = Object.keys(componentRegistry).filter(
      (name) => !examples.some((e) => e.includes(`<${name}`)),
    );
    expect(missing).toEqual([]);
  });
});
