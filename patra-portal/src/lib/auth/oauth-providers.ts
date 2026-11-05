/// 第三方登录支持的平台，路径和 JSON 里都是这三个小写值；顺序是页面上的固定顺序
export const PROVIDERS = ["google", "github", "orcid"] as const;

export type Provider = (typeof PROVIDERS)[number];

const NAMES: Record<Provider, string> = { google: "Google", github: "GitHub", orcid: "ORCID" };

/// 是不是支持的平台
export function isProvider(value: unknown): value is Provider {
  return typeof value === "string" && (PROVIDERS as readonly string[]).includes(value);
}

/// 平台的显示名
export function providerName(provider: Provider): string {
  return NAMES[provider];
}

/// 「刚用第三方账号登录」的短期标记：回调路由写入，下一页的脚本读取后清掉
export const SIGNED_IN_FLAG_COOKIE = "patra_oauth_signed_in";
