import { readFragmentToken } from "@/lib/identity/email-token";
import { LINK_CATEGORIES, type LinkCategory } from "@/lib/notification/schema";

/// 退订确认页地址片段里的令牌与类别（`#token=…&category=REPLY|MENTION|DIGEST`，PAP-142 第 7 节）。任一缺失或格式
/// 不对就是 null：落地页直接出无效屏，不请求后端。
export function readUnsubscribeFragment(
  hash: string,
): { token: string; category: LinkCategory } | null {
  const token = readFragmentToken(hash);
  const category = new URLSearchParams(hash.replace(/^#/, "")).get("category");
  const known = LINK_CATEGORIES.find((candidate) => candidate === category);
  return token && known ? { token, category: known } : null;
}
