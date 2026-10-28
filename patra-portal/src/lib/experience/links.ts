import type { PostDetail } from "@/lib/interaction/experience-schema";

/// 期刊还在：经验页上指向期刊页经验区的链接；期刊已失效时为 null，这些链接改成纯文字（spec 8.1）。
/// 放在纯模块里：经验页（RSC）与 `PostView`（客户端）都要用，RSC 不能调用 `"use client"` 模块的导出
export function journalHref(detail: PostDetail): string | null {
  return detail.journal.state === "ACTIVE" && detail.journal.key
    ? `/journals/${detail.journal.key}#experiences`
    : null;
}
