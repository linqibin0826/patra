import { describe, expect, it } from "vitest";
import { findAll, format, readSrc } from "./source-scan";

/// 找出不在 `@media (prefers-reduced-motion: no-preference)` 内的 animation 声明（`animation: none` 不算）。
function animationsOutsideNoPreference(css: string): string[] {
  const stack: string[] = [];
  const offenders: string[] = [];
  let buf = "";
  const check = (decl: string) => {
    const d = decl.trim();
    if (!/^animation(?:-name)?\s*:/.test(d) || /:\s*none\b/.test(d)) return;
    if (stack.some((p) => /prefers-reduced-motion:\s*no-preference/.test(p))) return;
    offenders.push(`${stack.join(" > ")} { ${d} }`);
  };
  for (const ch of css.replace(/\/\*[\s\S]*?\*\//g, "")) {
    if (ch === "{") {
      stack.push(buf.trim());
      buf = "";
    } else if (ch === "}") {
      check(buf);
      stack.pop();
      buf = "";
    } else if (ch === ";") {
      check(buf);
      buf = "";
    } else {
      buf += ch;
    }
  }
  return offenders;
}

describe("动效", () => {
  it("token 定义入场曲线与时长", () => {
    const tokens = readSrc("styles/tokens.css");
    expect(tokens).toMatch(/--ease-out-expo:\s*cubic-bezier\(0\.16, 1, 0\.3, 1\);/);
    expect(tokens).toMatch(/--dur-reveal:\s*\d+ms;/);
    expect(tokens).toMatch(/--dur-draw:\s*\d+ms;/);
  });

  it("globals.css 的 animation 声明全部包在 prefers-reduced-motion: no-preference 内", () => {
    expect(animationsOutsideNoPreference(readSrc("app/globals.css"))).toEqual([]);
  });

  it("组件里的 animate-* 必须带 motion-safe: 前缀（加载转圈 animate-spin 除外）", () => {
    const hits = findAll(/(?<![\w-])(?:[\w\-[\]=&>*:@.]+:)*animate-[\w[\]\-_.()%,]+/).filter(
      ({ match }) => !/(?:^|:)motion-safe:/.test(match) && !/animate-spin\b/.test(match),
    );
    expect(format(hits)).toEqual([]);
  });
});
