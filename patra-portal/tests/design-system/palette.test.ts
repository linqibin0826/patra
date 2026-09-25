import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { btnPrimary } from "@/lib/portal-ui";
import { readSrc } from "./source-scan";

describe("色板：陶土色是唯一强调色", () => {
  it("tokens.css 不再定义 teal 色阶", () => {
    expect(readSrc("styles/tokens.css")).not.toMatch(/--teal-/);
  });

  it("shadcn 主色槽位指向 action-primary，焦点环指向 border-focus", () => {
    const css = readSrc("app/globals.css");
    expect(css).toMatch(/--color-primary:\s*var\(--action-primary\);/);
    expect(css).toMatch(/--color-primary-foreground:\s*var\(--fg-on-clay\);/);
    expect(css).toMatch(/--color-ring:\s*var\(--border-focus\);/);
  });

  it("浏览器主题色与页面画布一致（paper-100）", () => {
    const layout = readFileSync(resolve(__dirname, "../../src/app/layout.tsx"), "utf8");
    expect(layout).toMatch(/themeColor:\s*"#f7f2e8"/);
  });

  it("主按钮使用 action-primary 三态", () => {
    expect(btnPrimary).toContain("bg-action-primary");
    expect(btnPrimary).toContain("hover:bg-action-primary-hover");
    expect(btnPrimary).toContain("active:bg-action-primary-press");
  });
});
