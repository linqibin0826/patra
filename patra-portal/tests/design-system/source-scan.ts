import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, resolve } from "node:path";

const ROOT = resolve(__dirname, "../..");
const SRC = join(ROOT, "src");

export interface SourceFile {
  path: string;
  text: string;
}

export interface Hit {
  file: string;
  line: number;
  match: string;
}

/// 读取 src 下全部 .ts / .tsx 源文件（样式类名只会出现在这里）。
export function sourceFiles(): SourceFile[] {
  const out: SourceFile[] = [];
  const walk = (dir: string) => {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name);
      if (statSync(full).isDirectory()) {
        walk(full);
      } else if (/\.tsx?$/.test(name) && !name.endsWith(".d.ts")) {
        out.push({ path: relative(ROOT, full), text: readFileSync(full, "utf8") });
      }
    }
  };
  walk(SRC);
  return out;
}

/// 读取 src 下的 CSS 文件原文。
export function readSrc(path: string): string {
  return readFileSync(join(SRC, path), "utf8");
}

/// 在全部源文件里找 pattern 的命中，返回 `文件:行 片段`，便于失败信息直接定位。
export function findAll(pattern: RegExp, files = sourceFiles()): Hit[] {
  const hits: Hit[] = [];
  const global = new RegExp(pattern.source, pattern.flags.includes("g") ? pattern.flags : `${pattern.flags}g`);
  for (const f of files) {
    f.text.split("\n").forEach((lineText, i) => {
      for (const m of lineText.matchAll(global)) {
        hits.push({ file: f.path, line: i + 1, match: m[0] });
      }
    });
  }
  return hits;
}

/// `@theme inline` 块里声明的全部 Tailwind 颜色名（`--color-<name>` 的 `<name>`）。
export function themeColors(): Set<string> {
  const css = readSrc("app/globals.css");
  const block = /@theme inline\s*\{([\s\S]*?)\n\}/.exec(css)?.[1] ?? "";
  return new Set([...block.matchAll(/--color-([a-z0-9-]+)\s*:/g)].map((m) => m[1] as string));
}

export const format = (hits: Hit[]): string[] => hits.map((h) => `${h.file}:${h.line} ${h.match}`);
