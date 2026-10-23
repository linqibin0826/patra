/// 前端能判的用户名问题，与 identity `Username` 的原因码同名、同判定顺序（release spec 2.1.1）。
/// 保留字、已被占用只由后端判。
export type UsernameIssue =
  | "REQUIRED"
  | "INVALID_CHARACTER"
  | "TOO_SHORT"
  | "TOO_LONG"
  | "INVALID_FORMAT"
  | "DIGITS_ONLY";

const ALLOWED = /^[a-z0-9_-]*$/;
/// 首尾是字母或数字，`_`、`-` 不连着用
const FORMAT = /^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$/;

/// 和后端一样：去首尾空白、转小写
export function normalizeUsername(raw: string): string {
  return raw.trim().toLowerCase();
}

/// 规范化之后按顺序判：为空 → 有不允许的字符 → 少于 3 → 多于 30 → 格式 → 全是数字。合规时为 null。
export function usernameIssue(raw: string): UsernameIssue | null {
  const value = normalizeUsername(raw);
  if (value === "") return "REQUIRED";
  if (!ALLOWED.test(value)) return "INVALID_CHARACTER";
  if (value.length < 3) return "TOO_SHORT";
  if (value.length > 30) return "TOO_LONG";
  if (!FORMAT.test(value)) return "INVALID_FORMAT";
  if (/^\d+$/.test(value)) return "DIGITS_ONLY";
  return null;
}
