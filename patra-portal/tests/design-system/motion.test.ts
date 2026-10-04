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

  it("link-draw 自带下划线与文字颜色两种过渡", () => {
    const block =
      /@utility link-draw\s*\{([\s\S]*?)\n\}/.exec(readSrc("app/globals.css"))?.[1] ?? "";
    const transition = /transition:\s*([^;]+);/.exec(block)?.[1] ?? "";
    expect(transition).toMatch(/background-size/);
    expect(transition).toMatch(/(?<![\w-])color(?![\w-])/);
  });

  it("link-draw 不与 transition-* 写在同一处：后者会盖掉下划线的 background-size 过渡", () => {
    const hits = findAll(/^.*(?<![\w-])link-draw(?![\w-]).*$/).filter(({ match }) =>
      /(?<![\w-])(?:[\w\-[\]=&>*:@./]+:)*transition(?:-[\w[\],-]+)?(?![\w-])/.test(match),
    );
    expect(format(hits)).toEqual([]);
  });

  it("减弱动效时，没有显式过渡属性的元素不过渡（初始值 all 会让不带前缀的 duration-* 照样把位移过渡出来）", () => {
    const css = readSrc("app/globals.css").replace(/\/\*[\s\S]*?\*\//g, "");
    const base = /@layer base\s*\{([\s\S]*?)\n\}/.exec(css)?.[1] ?? "";
    expect(base).toMatch(
      /@media \(prefers-reduced-motion: reduce\)\s*\{\s*\*,\s*::before,\s*::after\s*\{\s*transition-property:\s*none;/,
    );
  });

  it("位移与几何属性的过渡只在 motion-safe 下存在（状态照常切换，只是不带动画；开关旋钮靠 background-position 移动）", () => {
    const hits = findAll(
      /(?<![\w-])(?:[\w\-[\]=&>*:@./()%,]+:)*transition-(?:transform|\[[^\]\s]*(?:translate|scale|rotate|transform|left|right|top|bottom|width|height|background-position)[^\]\s]*\])(?![\w-])/,
    ).filter(({ match }) => !/(?:^|:)motion-safe:/.test(match));
    expect(format(hits)).toEqual([]);
  });

  it("用通用 transition 的元素上，状态触发的位移带 motion-safe: 前缀", () => {
    const bareTransition = /(?<![\w\-:[])transition(?:-all)?(?![\w-])/;
    const variantTransform =
      /(?<![\w-])(?:[\w\-[\]=&>*:@./()%,]+:)+-?(?:translate|scale|rotate)-[\w\-.[\]()%/]+(?![\w-])/g;
    const hits = findAll(/^.*$/)
      .filter(({ match }) => bareTransition.test(match))
      .flatMap((hit) =>
        [...hit.match.matchAll(variantTransform)]
          .map((m) => m[0])
          .filter((t) => !/(?:^|:)motion-safe:/.test(t))
          .map((t) => ({ ...hit, match: t })),
      );
    expect(format(hits)).toEqual([]);
  });

  it("组件里的 animate-* 必须带 motion-safe: 前缀（加载转圈 animate-spin 除外）", () => {
    const hits = findAll(/(?<![\w-])(?:[\w\-[\]=&>*:@.]+:)*animate-[\w[\]\-_.()%,]+/).filter(
      ({ match }) => !/(?:^|:)motion-safe:/.test(match) && !/animate-spin\b/.test(match),
    );
    expect(format(hits)).toEqual([]);
  });
});
