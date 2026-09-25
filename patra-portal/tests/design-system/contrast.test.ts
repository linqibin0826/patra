import { describe, expect, it } from "vitest";
import { findAll, format } from "./source-scan";

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

describe("写死的样式值", () => {
  it("className 不写 rgba / color-mix / 自定义阴影，改用 token", () => {
    expect(format(findAll(/rgba\(|color-mix\(|shadow-\[|shadow-inner\b/))).toEqual([]);
  });
});
