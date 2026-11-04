import { isProvider, type Provider } from "@/lib/auth/oauth-providers";

const KINDS = [
  "linked",
  "cancelled",
  /// 协议失败、发起人对不上、门户核对 `state` 失败：读者能做的都一样，不区分
  "rejected",
  /// 连不上平台（503）
  "unreachable",
  "banned",
  "taken",
  "already-linked",
  "unavailable",
] as const;

/// 第三方授权回到发起页时的结果
export type OAuthNoticeKind = (typeof KINDS)[number];

export interface OAuthNotice {
  kind: OAuthNoticeKind;
  provider: Provider;
}

/// 查询参数里取第一个值
function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/// 从发起页的查询参数取结果。两个都是枚举值，认不出就是没有：地址栏里的参数谁都能改，只能选固定的文案
export function parseOAuthNotice(
  params: Record<string, string | string[] | undefined>,
): OAuthNotice | null {
  const kind = KINDS.find((candidate) => candidate === first(params.oauth));
  const provider = first(params.provider);
  return kind && isProvider(provider) ? { kind, provider } : null;
}

/// 带上结果的发起页地址。只放这两个枚举值，`ticket`、`code`、`state` 不进地址栏
export function oauthNoticeHref(path: string, notice: OAuthNotice): string {
  const query = new URLSearchParams({ oauth: notice.kind, provider: notice.provider });
  return `${path}${path.includes("?") ? "&" : "?"}${query}`;
}
