import { diffArrays } from "diff";
import type { Nodes } from "mdast";
import { fromMarkdown } from "mdast-util-from-markdown";
import type { Mention } from "@/lib/interaction/discussion-schema";
import { MENTION_FROM_MARKDOWN, SUBSET_SYNTAX } from "@/lib/markdown/subset";

/// 修改历史里一段文字与上一版相比：相同、新增、删掉
export type DiffPart = { kind: "same" | "added" | "removed"; text: string };

/// 中文按词、英文按单词切：比按字符比较可读
const WORDS = new Intl.Segmenter("zh", { granularity: "word" });

function words(value: string): string[] {
  return Array.from(WORDS.segment(value), (part) => part.segment);
}

/// 两版原文的增删（简报 §3.8）：`previous` 是时间上更早的那版
export function diffVersions(previous: string, current: string): DiffPart[] {
  const parts: DiffPart[] = [];
  for (const change of diffArrays(words(previous), words(current))) {
    const kind = change.added ? "added" : change.removed ? "removed" : "same";
    const text = change.value.join("");
    const last = parts.at(-1);
    if (last?.kind === kind) {
      last.text += text;
    } else if (text) {
      parts.push({ kind, text });
    }
  }
  return parts;
}

/// 一个提及标记在原文里的位置
function markerRanges(node: Nodes, out: { start: number; end: number; userId: string }[]) {
  if (node.type === "mention") {
    const start = node.position?.start.offset;
    const end = node.position?.end.offset;
    if (start !== undefined && end !== undefined) {
      out.push({ start, end, userId: node.userId });
    }
    return;
  }
  if ("children" in node) {
    for (const child of node.children as Nodes[]) {
      markerRanges(child, out);
    }
  }
}

/// 修改历史显示的原文里，服务端解析出的 `<@用户ID>` 换成人看得懂的 `@用户名` / `@昵称` / `@已注销用户`；
/// 转义的 `\<@…>` 与行内代码里的照原样（与渲染同一套解析，后端 5.4.3）
export function resolveMentionMarkers(body: string, mentions: readonly Mention[]): string {
  const byId = new Map(mentions.map((mention) => [mention.userId, mention]));
  const ranges: { start: number; end: number; userId: string }[] = [];
  markerRanges(
    fromMarkdown(body, { extensions: SUBSET_SYNTAX, mdastExtensions: [MENTION_FROM_MARKDOWN] }),
    ranges,
  );
  let out = body;
  for (const { start, end, userId } of ranges.reverse()) {
    const mention = byId.get(userId);
    const name =
      !mention || mention.deleted
        ? "已注销用户"
        : (mention.username ?? mention.displayName ?? "已注销用户");
    out = `${out.slice(0, start)}@${name}${out.slice(end)}`;
  }
  return out;
}
