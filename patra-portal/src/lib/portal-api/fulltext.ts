import "server-only";
import { cache } from "react";
import { gatewayFetch } from "@/lib/gateway";
import { fullTextSchema } from "@/lib/portal-api/fulltext-schema";
import type { FullText } from "@/types/fulltext";

/**
 * 在服务端拉取一篇文献的全文（正文结构 + 许可与出处）。仅 RSC 调用（server-only）。
 *
 * 端点：`GET /patra-catalog/portal/publications/{id}/fulltext`。
 * 返回约定：
 * - 非正整数 id → `null`，不触达 BE
 * - 404（既读不了正文也下不了 PDF）→ `null`
 * - 其他失败（5xx / 超时 / 结构对不上白名单）→ throw，由全文区块显示「全文没能加载」
 *
 * 用 `cache` 包一层：同一次渲染里全文区块、目录卡、目录按钮各自取数，只发一次请求。
 */
export const fetchPublicationFullText = cache(async (id: string): Promise<FullText | null> => {
  if (!/^[1-9]\d*$/.test(id)) {
    return null;
  }
  const res = await gatewayFetch(`/patra-catalog/portal/publications/${id}/fulltext`);
  if (res.status === 404) {
    return null;
  }
  if (!res.ok) {
    throw new Error(`全文加载失败：${res.status}`);
  }
  const parsed = fullTextSchema.safeParse(await res.json());
  if (!parsed.success) {
    throw new Error(`全文结构不符合约定：${parsed.error.issues[0]?.path.join(".") ?? ""}`);
  }
  return parsed.data;
});
