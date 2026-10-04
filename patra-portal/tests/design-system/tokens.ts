import { readSrc } from "./source-scan";

/// 解析 tokens.css 的变量定义，递归展开 var() 到最终取值。
export function resolveToken(name: string, css = readSrc("styles/tokens.css")): string {
  const map = new Map(
    [...css.matchAll(/--([a-z0-9-]+):\s*([^;]+);/g)].map((m) => [
      m[1] as string,
      (m[2] as string).trim(),
    ]),
  );
  let value = map.get(name);
  for (let guard = 0; value?.startsWith("var(") && guard < 10; guard++) {
    value = map.get(/var\(--([a-z0-9-]+)\)/.exec(value)?.[1] ?? "");
  }
  if (!value) {
    throw new Error(`token --${name} 未定义`);
  }
  return value;
}

const channel = (c: number) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);

function luminance(hex: string): number {
  if (!/^#[0-9a-f]{6}$/i.test(hex)) {
    throw new Error(`只接受 #rrggbb：${hex}`);
  }
  const [r = 0, g = 0, b = 0] = [1, 3, 5].map((i) =>
    channel(Number.parseInt(hex.slice(i, i + 2), 16) / 255),
  );
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/// WCAG 2 对比度（仅接受 #rrggbb）。
export function contrastRatio(fg: string, bg: string): number {
  const [hi = 0, lo = 0] = [luminance(fg), luminance(bg)].sort((a, b) => b - a);
  return (hi + 0.05) / (lo + 0.05);
}
