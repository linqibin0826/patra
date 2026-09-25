import { describe, expect, it } from "vitest";
import { findAll, format, themeColors } from "./source-scan";

/// 颜色类工具前缀 → 该前缀下「不是颜色」的取值（宽度、样式、尺寸等）。
const NON_COLOR: Record<string, RegExp> = {
  text: /^(\d?xs|sm|base|md|lg|\d?xl|left|center|right|justify|start|end|balance|pretty|nowrap|wrap|ellipsis|clip)$/,
  bg: /^(clip-.+|origin-.+|fixed|local|scroll|auto|cover|contain|center|top|bottom|left|right|no-repeat|repeat.*|none|gradient-.+|linear.*|radial.*|conic.*|blend-.+)$/,
  border:
    /^(\d+|x|y|t|b|l|r|s|e|[xytblrse]-\d+|solid|dashed|dotted|double|hidden|none|collapse|separate|spacing.*)$/,
  ring: /^(\d+|inset|offset-.+)$/,
  outline: /^(\d+|none|hidden|dashed|dotted|double|solid|offset-.+)$/,
  fill: /^(none|\d+)$/,
  stroke: /^(none|\d+)$/,
  divide: /^(\d+|x|y|[xy]-\d+|[xy]-reverse|solid|dashed|dotted|double|none)$/,
  decoration: /^(\d+|solid|double|dotted|dashed|wavy|auto|from-font|clone|slice)$/,
  accent: /^auto$/,
  placeholder: /^$/,
  caret: /^$/,
};

/// Tailwind 自带、不属于任何色板的颜色关键字。
const KEYWORDS = new Set(["transparent", "current", "inherit"]);

const UTILITY =
  /(?<![\w\-(])(?:[\w\-[\]=&>*:@.]+:)*!?(text|bg|border(?:-[xytblrse])?|ring|outline|fill|stroke|divide|decoration|accent|placeholder|caret)-([a-z][a-z0-9-]*)(?:\/\d+)?!?(?![\w-])/;

describe("颜色类工具", () => {
  it("只引用 @theme 里声明过的颜色（否则编译后不生效）", () => {
    const colors = themeColors();
    const offenders = findAll(UTILITY).filter(({ match }) => {
      const m = UTILITY.exec(match);
      if (!m) return false;
      const prefix = (m[1] as string).replace(/-[xytblrse]$/, "");
      const value = m[2] as string;
      if (NON_COLOR[prefix]?.test(value)) return false;
      if (KEYWORDS.has(value)) return false;
      return !colors.has(value);
    });
    expect(format(offenders)).toEqual([]);
  });
});

describe("颜色的写法", () => {
  it("@theme 已映射的颜色用标准类名，不用 -(--x) 或 [var(--x)] 临时写法", () => {
    const colors = themeColors();
    const offenders = findAll(/-(?:\(--([a-z0-9-]+)\)|\[var\(--([a-z0-9-]+)\)\])/).filter(
      ({ match }) => {
        const name = /--([a-z0-9-]+)/.exec(match)?.[1] ?? "";
        return colors.has(name);
      },
    );
    expect(format(offenders)).toEqual([]);
  });
});
