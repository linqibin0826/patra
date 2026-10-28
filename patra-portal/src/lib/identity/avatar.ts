import { graphemes } from "@/lib/grapheme";

/// 默认头像的 12 色板，按 `avatarPalette` 色号 0–11 排列（简报 §3.20）。全部取自现有 token，纸色字在
/// 每一种上都 ≥ 4.5:1。写成字面量：Tailwind 只生成源码里完整出现的类名。
export const AVATAR_PALETTE = [
  "bg-clay-700",
  "bg-slate-500",
  "bg-moss-500",
  "bg-ink-700",
  "bg-rust-500",
  "bg-amber-700",
  "bg-clay-900",
  "bg-moss-700",
  "bg-ink-500",
  "bg-clay-800",
  "bg-ink-900",
  "bg-ink-600",
] as const;

/// 色号 → 底色类名，越界时取模
export function avatarPaletteClass(index: number): string {
  const size = AVATAR_PALETTE.length;
  return AVATAR_PALETTE[((index % size) + size) % size] as string;
}

/// 跳过的开头字符：标点与空白
const SKIPPED = /^[\p{P}\p{White_Space}]+$/u;

/// 默认头像上的字（简报 §3.20）：昵称里第一个不是标点或空白的字素，拉丁字母转大写，其余原样；
/// 昵称里没有可取的字时用用户名的首字母（主页对访客不可见时没有用户名，为空）。
export function avatarInitial(displayName: string, username: string | null): string {
  const first = graphemes(displayName).find((part) => !SKIPPED.test(part));
  return (first ?? (username ?? "").slice(0, 1)).toUpperCase();
}

/// identity 给的头像地址：`/users/{用户 ID}/avatar?v={16 位十六进制}`（v0.13 起按用户 ID，改名不失效）
const AVATAR_URL = /^\/users\/([1-9][0-9]{0,18})\/avatar\?v=([0-9a-f]{16})$/;

/// identity 的头像相对地址 → 门户同源的转发地址，追加尺寸档（36 / 40px 用 128，72px 及以上用 512）。
/// 形状不对时为 null，调用方显示默认头像。
export function avatarSrc(avatarUrl: string | null, size: 128 | 512): string | null {
  const match = avatarUrl === null ? null : AVATAR_URL.exec(avatarUrl);
  if (!match) {
    return null;
  }
  const [, userId, version] = match;
  return `/api/users/${userId}/avatar?v=${version}&size=${size}`;
}
