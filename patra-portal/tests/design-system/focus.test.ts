import { describe, expect, it } from "vitest";
import { findAll, format, readSrc } from "./source-scan";

describe("键盘焦点", () => {
  it("ring-focus 是 2px 纸色间隙 + 2px clay-600 实环", () => {
    expect(readSrc("styles/tokens.css")).toMatch(
      /--ring-focus:\s*0 0 0 2px #fbf8f2,\s*0 0 0 4px #b85727;/,
    );
  });

  it("全局（不分层）为全部非文本可交互元素统一设置 :focus-visible 焦点环", () => {
    const css = readSrc("app/globals.css");
    const rule = /:where\(([^)]*(?:\([^)]*\)[^)]*)*)\):focus-visible\s*\{([^}]*)\}/.exec(css);
    expect(rule, "globals.css 缺少 :where(...):focus-visible 规则").not.toBeNull();
    const [, selectors = "", body = ""] = rule ?? [];
    for (const s of ["a", "button", "summary", '[role="tab"]', '[type="checkbox"]', "[tabindex]"]) {
      expect(selectors).toContain(s);
    }
    expect(body).toMatch(/outline:\s*none/);
    expect(body).toMatch(/box-shadow:\s*var\(--ring-focus\)/);
    // 规则所在位置的花括号深度为 0，即不在任何 @layer 内
    const before = css.slice(0, rule?.index ?? 0);
    const depth = (before.match(/\{/g)?.length ?? 0) - (before.match(/\}/g)?.length ?? 0);
    expect(depth, "焦点规则必须放在 @layer 之外").toBe(0);
  });

  it("组件不再自带焦点环类（统一走全局规则，避免覆盖与不一致）", () => {
    expect(format(findAll(/focus-visible:(?:ring|shadow|outline)[\w\-[\]()/!]*/))).toEqual([]);
  });

  it("文本框聚焦时描边统一为 border-focus", () => {
    expect(
      format(findAll(/focus-(?:visible|within):border-(?!border-focus\b)[\w\-[\]()/!]*/)),
    ).toEqual([]);
  });
});
