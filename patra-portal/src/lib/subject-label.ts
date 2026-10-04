/// 句中保持小写的英文虚词
const MINOR_WORDS = new Set([
  "a",
  "an",
  "and",
  "as",
  "at",
  "for",
  "in",
  "of",
  "on",
  "or",
  "the",
  "to",
  "with",
]);

/// 全大写英文词（可含连字符 / 撇号 / 数字），至少两个字母
const ALL_CAPS_WORD = /^[A-Z][A-Z0-9'-]*[A-Z0-9]$/;

const capitalize = (part: string) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase();

/// JCR / 中科院学科名展示用：把全大写英文词转成标题式大小写（「MEDICINE, GENERAL & INTERNAL」→
/// 「Medicine, General & Internal」），句中虚词小写、连字符两侧分别首字母大写；中文与已有大小写的词原样保留。
export function formatSubject(raw: string): string {
  let wordIndex = 0;
  return raw.replace(/[A-Za-z0-9'-]+/g, (token) => {
    const index = wordIndex++;
    if (!ALL_CAPS_WORD.test(token)) {
      return token;
    }
    const lower = token.toLowerCase();
    if (index > 0 && MINOR_WORDS.has(lower)) {
      return lower;
    }
    return token.split("-").map(capitalize).join("-");
  });
}
