// patra-learn/src/lib/content/catalog.ts
// 页面在 next build 预渲染时读内容；纯静态站，运行时不读 content/（standalone 镜像里也没有它）
import { readContent } from "./load";
import { parseContent } from "./parse";
import type { ContentCatalog } from "./types";

let cached: ContentCatalog | undefined;

/// 读并解析 process.cwd() 下的内容；有解析问题时直接让构建失败
export function getCatalog(): ContentCatalog {
  if (cached) return cached;
  const { catalog, issues } = parseContent(readContent(process.cwd()));
  if (issues.length > 0) {
    const list = issues.map((i) => `  [${i.rule}] ${i.file}：${i.message}`).join("\n");
    throw new Error(`内容解析失败，先跑 pnpm test 看完整校验：\n${list}`);
  }
  cached = catalog;
  return catalog;
}
