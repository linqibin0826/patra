import { describe, expect, it } from "vitest";
import { findAll, format, readSrc } from "./source-scan";

describe("字号与字距", () => {
  it("字号阶新增 text-3xs（9.5px，全站下限），tracking 新增 mono / spaced", () => {
    const tokens = readSrc("styles/tokens.css");
    expect(tokens).toMatch(/--text-3xs:\s*9\.5px;/);
    expect(tokens).toMatch(/--tracking-mono:\s*0\.06em;/);
    expect(tokens).toMatch(/--tracking-spaced:\s*0\.14em;/);
    const theme = readSrc("app/globals.css");
    for (const name of ["text-3xs", "tracking-mono", "tracking-spaced"]) {
      expect(theme).toContain(`--${name}: var(--${name});`);
    }
  });

  it("不写死像素字号，一律用字号阶（响应式 clamp() 除外）", () => {
    expect(format(findAll(/text-\[\d+(?:\.\d+)?px\]/))).toEqual([]);
  });

  it("不写零散字距，一律用 tracking token", () => {
    expect(format(findAll(/tracking-\[[^\]]+\]/))).toEqual([]);
  });
});
