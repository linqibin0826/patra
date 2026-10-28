import { graphemeCount } from "@/lib/grapheme";

/// 评论正文的上限（后端 spec 5.3、5.4.2）：5,000 字素、20,000 字节（字素上限 × 4）、@ 10 人
export const COMMENT_LIMITS = { graphemes: 5000, bytes: 20000, mentions: 10 } as const;

/// 计数之前按后端的规则规整：换行统一成 `\n`、NFC、去首尾空白
export function normalizeForCount(text: string): string {
  return text.replace(/\r\n?/g, "\n").normalize("NFC").trim();
}

const ENCODER = new TextEncoder();

/// 规整后的字素数与 UTF-8 字节数
export function measure(text: string): { graphemes: number; bytes: number } {
  const normalized = normalizeForCount(text);
  return { graphemes: graphemeCount(normalized), bytes: ENCODER.encode(normalized).length };
}

/// `near`：达到字素上限的 80%；超字素优先于超字节
export type LimitState = "ok" | "near" | "over-graphemes" | "over-bytes";

export function limitState(
  m: { graphemes: number; bytes: number },
  limits: { graphemes: number; bytes: number },
): LimitState {
  if (m.graphemes > limits.graphemes) {
    return "over-graphemes";
  }
  if (m.bytes > limits.bytes) {
    return "over-bytes";
  }
  return m.graphemes >= limits.graphemes * 0.8 ? "near" : "ok";
}

/// 行内代码、链接 `[文字](目标)`、网址：里面的 `@` 不是提及
const NOT_MENTION_SPANS = /(`+)[\s\S]*?\1|\[[^\]\n]*\]\([^)\n]*\)|https?:\/\/[^\s<>]+/g;
/// identity 的用户名语法：3 ~ 30 个字符、字母数字开头结尾、`_` `-` 不连续、不全是数字
const USERNAME = /^[A-Za-z0-9](?:[A-Za-z0-9]|[_-][A-Za-z0-9])*$/;

function isUsername(name: string): boolean {
  return name.length >= 3 && name.length <= 30 && USERNAME.test(name) && !/^\d+$/.test(name);
}

/// `@` 前面是字母、数字、`_`、`@` 时不是提及（邮箱、`你好@chen`）
const WORD_BEFORE = /[\p{L}\p{N}_@]/u;

/// 正文里会被服务端当成 @ 的用户名（小写去重，保持首次出现的顺序），只用来粗算人数；以服务端解析为准
export function mentionCandidates(text: string): string[] {
  const masked = text.replace(NOT_MENTION_SPANS, (span) => " ".repeat(span.length));
  const seen = new Set<string>();
  for (const match of masked.matchAll(/@([A-Za-z0-9_-]+)/g)) {
    const before = match.index > 0 ? masked[match.index - 1] : undefined;
    if (before !== undefined && WORD_BEFORE.test(before)) {
      continue;
    }
    const name = (match[1] as string).replace(/[_-]+$/, "");
    if (isUsername(name)) {
      seen.add(name.toLowerCase());
    }
  }
  return [...seen];
}
