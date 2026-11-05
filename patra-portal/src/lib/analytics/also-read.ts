import type { AlsoReadItem } from "@/lib/analytics/insight-schema";

/// 「看过这篇的人还看了」的一组：一起读的（CO_READ）一组，同主要主题词与同期刊的补位合成「同主题文献」一组
export interface AlsoReadGroup {
  kind: "co-read" | "topic";
  items: AlsoReadItem[];
}

/// 按来源分组（简报 v0.18 §3.6）：保持后端给的顺序；去掉当前这篇与重复的 ID；空的组不返回
export function groupAlsoRead(items: readonly AlsoReadItem[], currentId: string): AlsoReadGroup[] {
  const seen = new Set<string>([currentId]);
  const coRead: AlsoReadItem[] = [];
  const topic: AlsoReadItem[] = [];
  for (const item of items) {
    if (seen.has(item.articleId)) {
      continue;
    }
    seen.add(item.articleId);
    (item.source === "CO_READ" ? coRead : topic).push(item);
  }
  const groups: AlsoReadGroup[] = [];
  if (coRead.length > 0) groups.push({ kind: "co-read", items: coRead });
  if (topic.length > 0) groups.push({ kind: "topic", items: topic });
  return groups;
}
