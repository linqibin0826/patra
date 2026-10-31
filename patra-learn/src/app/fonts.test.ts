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

/// 取 @theme 里某个字体 token 的整条字族栈
function stack(token: string) {
  const value = css.match(new RegExp(`${token}:\\s*([^;]+);`))?.[1] ?? "";
  return value
    .split(",")
    .map((f) => f.trim().replace(/^"|"$/g, ""))
    .filter(Boolean);
}

describe("构建时不联网下载字体（决策 G）", () => {
  it("layout 不用 next/font/google（构建时下载字体，Turbopack 下任一请求失败即构建失败）", () => {
    expect(layout).not.toMatch(/next\/font\/google/);
  });

  it("不下载中文网页字体：引入的字体包只有 Source Serif 4 与 JetBrains Mono", () => {
    const imports = [...layout.matchAll(/import "(@fontsource[^"]+)";/g)].map((m) => m[1]);
    expect(imports.sort()).toEqual([
      "@fontsource-variable/jetbrains-mono",
      "@fontsource-variable/source-serif-4",
    ]);
  });

  it("标题与正文：西文首选自托管的 Source Serif 4，中文落到系统宋体 / 系统黑体", () => {
    const serif = fontsourceFamily("@fontsource-variable/source-serif-4");
    expect(stack("--font-display")[0]).toBe(serif);
    expect(stack("--font-display")).toContain("Songti SC");
    expect(stack("--font-body")[0]).toBe(serif);
    expect(stack("--font-body")).toContain("PingFang SC");
  });

  it("控件用系统 UI 字体，代码首选自托管的 JetBrains Mono", () => {
    expect(stack("--font-ui")[0]).toBe("system-ui");
    expect(stack("--font-mono")[0]).toBe(fontsourceFamily("@fontsource-variable/jetbrains-mono"));
  });
});
