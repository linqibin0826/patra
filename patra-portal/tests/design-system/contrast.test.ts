import { describe, expect, it } from "vitest";
import { findAll, format } from "./source-scan";
import { contrastRatio, resolveToken } from "./tokens";

describe("文字对比度", () => {
  it("可读文字不浅于 fg-3：fg-4 / ink-400 / ink-500 只用于占位符与禁用态", () => {
    const hits = findAll(
      /(?<![\w-])((?:[\w\-[\]=&>*:@.]+:)*)!?text-(?:fg-4|ink-400|ink-500)(?:\/\d+)?(?![\w-])/,
    ).filter(
      ({ match }) => !/(?:^|:)(?:placeholder|disabled|aria-disabled|data-disabled):/.test(match),
    );
    expect(format(hits)).toEqual([]);
  });

  it("成功 / 警示文字用 status-*-ink（moss-500、amber-500 在浅底上不达 4.5:1）", () => {
    expect(format(findAll(/(?<![\w-])(?:[\w\-[\]=&>*:@.]+:)*text-(?:moss|amber)-500\b/))).toEqual(
      [],
    );
  });
});

describe("墨色反相区", () => {
  it("反相文字 token 在 bg-inverse 上对比度达标（正文 ≥ 7:1，强调 ≥ 4.5:1）", () => {
    const bg = resolveToken("bg-inverse");
    for (const fg of ["fg-inverse-1", "fg-inverse-2", "fg-inverse-3"]) {
      expect(contrastRatio(resolveToken(fg), bg), fg).toBeGreaterThanOrEqual(7);
    }
    expect(contrastRatio(resolveToken("accent-on-inverse"), bg)).toBeGreaterThanOrEqual(4.5);
  });
});

describe("写死的样式值", () => {
  it("className 不写 rgba / color-mix / 自定义阴影，改用 token", () => {
    expect(format(findAll(/rgba\(|color-mix\(|shadow-\[|shadow-inner\b/))).toEqual([]);
  });
});
