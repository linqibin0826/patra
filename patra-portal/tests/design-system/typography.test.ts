import { describe, expect, it } from "vitest";
import { findAll, format, readSrc } from "./source-scan";

describe("中文破折号", () => {
  it("「——」前后不加空格（中文排版惯例）", () => {
    expect(format(findAll(/\S ——|—— \S/))).toEqual([]);
  });
});

describe("字号与字距", () => {
  it("字号阶整体 +0.5px：下限 text-3xs 为 10px、base 14px；字距含 mono / spaced / display", () => {
    const tokens = readSrc("styles/tokens.css");
    expect(tokens).toMatch(/--text-3xs:\s*10px;/);
    expect(tokens).toMatch(/--text-base:\s*14px;/);
    expect(tokens).toMatch(/--tracking-mono:\s*0\.06em;/);
    expect(tokens).toMatch(/--tracking-spaced:\s*0\.14em;/);
    expect(tokens).toMatch(/--tracking-display:\s*-0\.035em;/);
    const theme = readSrc("app/globals.css");
    for (const name of [
      "text-3xs",
      "tracking-mono",
      "tracking-spaced",
      "tracking-display",
      "leading-display",
    ]) {
      expect(theme).toContain(`--${name}: var(--${name});`);
    }
  });

  it("流体显示字号 display-1/2/3 用 clamp() 定义并映射到 @theme", () => {
    const tokens = readSrc("styles/tokens.css");
    const theme = readSrc("app/globals.css");
    for (const n of [1, 2, 3]) {
      expect(tokens).toMatch(new RegExp(`--text-display-${n}:\\s*clamp\\(`));
      expect(theme).toContain(`--text-display-${n}: var(--text-display-${n});`);
    }
  });

  it("字体：Newsreader 衬线 + IBM Plex Mono 等宽，等宽栈带中文回退", () => {
    const tokens = readSrc("styles/tokens.css");
    expect(tokens).toMatch(/--patra-font-serif:\s*var\(--font-newsreader\)/);
    expect(tokens).toMatch(
      /--patra-font-mono:\s*var\(--font-plex-mono\),\s*var\(--font-noto-sans-sc\)/,
    );
    const layout = readSrc("app/layout.tsx");
    expect(layout).toMatch(/Newsreader/);
    expect(layout).toMatch(/IBM_Plex_Mono/);
    expect(layout).not.toMatch(/Source_Serif_4|JetBrains_Mono/);
  });

  it("不写死像素字号，一律用字号阶（响应式 clamp() 除外）", () => {
    expect(format(findAll(/text-\[\d+(?:\.\d+)?px\]/))).toEqual([]);
  });

  it("不写零散字距，一律用 tracking token", () => {
    expect(format(findAll(/tracking-\[[^\]]+\]/))).toEqual([]);
  });
});
