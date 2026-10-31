// patra-learn/src/lib/content/load.ts
// 从磁盘读内容原文。只读 content/entries/*.mdx、routes/*.yaml、topics/*.yaml 与 tags.yaml，
// content/ 下其他文件（如 PAP-153 的 curriculum.md）一律不碰
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import type { RawContent, RawFile } from "./types";

/// 读 content/<dir>/ 下指定扩展名的文件，按文件名排序；目录不存在时为空
function readDir(learnRoot: string, dir: string, extension: string): RawFile[] {
  const absolute = join(learnRoot, "content", dir);
  if (!existsSync(absolute)) return [];
  return readdirSync(absolute)
    .filter((name) => name.endsWith(extension))
    .sort()
    .map((name) => ({
      file: `content/${dir}/${name}`,
      text: readFileSync(join(absolute, name), "utf8"),
    }));
}

/// learnRoot 是 patra-learn 目录
export function readContent(learnRoot: string): RawContent {
  const tagsPath = join(learnRoot, "content", "tags.yaml");
  return {
    entries: readDir(learnRoot, "entries", ".mdx"),
    routes: readDir(learnRoot, "routes", ".yaml"),
    topics: readDir(learnRoot, "topics", ".yaml"),
    tags: existsSync(tagsPath)
      ? { file: "content/tags.yaml", text: readFileSync(tagsPath, "utf8") }
      : null,
  };
}

/// 代码引用用：判断仓库里（learnRoot 的上一级）某个文件是否存在
export function repoFileExists(learnRoot: string): (repoPath: string) => boolean {
  const repoRoot = join(learnRoot, "..");
  return (repoPath) => {
    const absolute = join(repoRoot, repoPath);
    return existsSync(absolute) && statSync(absolute).isFile();
  };
}
