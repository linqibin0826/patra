/// 手敲日期的解析：认 `2026-08-14`、`2026/8/14`、`2026.8.14`、`20260814`、`2026年8月14日`，
/// 规范成 `YYYY-MM-DD`；认不出或日期不存在（9 月 31 日）返回 `null`

const SEPARATED = /^(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*日?$/;
const COMPACT = /^(\d{4})(\d{2})(\d{2})$/;
const DAY_MS = 86_400_000;

const pad = (n: number) => String(n).padStart(2, "0");

/// 解析手敲的日期，返回 `YYYY-MM-DD` 或 `null`
export function parseDateInput(text: string): string | null {
  const trimmed = text.trim();
  const match = SEPARATED.exec(trimmed) ?? COMPACT.exec(trimmed);
  if (!match) {
    return null;
  }
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(Date.UTC(year, month - 1, day));
  if (date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
    return null;
  }
  return `${year}-${pad(month)}-${pad(day)}`;
}

/// 两个 `YYYY-MM-DD` 相差的天数（`to - from`）
export function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY_MS);
}
