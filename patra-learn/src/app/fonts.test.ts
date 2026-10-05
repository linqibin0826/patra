import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const css = readFileSync(join(import.meta.dirname, "globals.css"), "utf8");
const layout = readFileSync(join(import.meta.dirname, "layout.tsx"), "utf8");
const require = createRequire(import.meta.url);

/// 读取 @fontsource 包自带样式里声明的字族名
function fontsourceFamily(pkg: string) {
  const face = readFileSync(require.resolve(`${pkg}/index.css`), "utf8");
  return face.match(/font-family:\s*'([^']+)'/)?.[1];
}

/// 取 @theme 里某个字体 token 的首选字族
function firstFamily(token: string) {
  return css.match(new RegExp(`${token}:\\s*"([^"]+)"`))?.[1];
}

describe("构建时不联网下载字体", () => {
  it("layout 不用 next/font/google（构建时下载字体，Turbopack 下任一请求失败即构建失败）", () => {
    expect(layout).not.toMatch(/next\/font\/google/);
  });

  it("正文用系统字体：西文系统 UI 字体，中文系统黑体（与 portal 同一组），不下载中文网页字体", () => {
    expect(layout).not.toMatch(/noto-sans-sc/);
    expect(css).toMatch(
      /--font-sans:\s*ui-sans-serif, system-ui, "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", sans-serif;/,
    );
  });

  it("等宽字体首选 @fontsource-variable/jetbrains-mono", () => {
    expect(layout).toMatch(/import "@fontsource-variable\/jetbrains-mono";/);
    expect(firstFamily("--font-mono")).toBe(
      fontsourceFamily("@fontsource-variable/jetbrains-mono"),
    );
  });
});
