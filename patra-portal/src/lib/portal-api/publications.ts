import "server-only";
import { gatewayFetch } from "@/lib/gateway";
import type { FeedTab, PageResult, Paper, PaperDetail } from "@/types/portal";

/**
 * 在服务端拉取 portal 文献流。仅 RSC / Route Handler 调用（server-only）。
 * 通过 gateway 访问 catalog，gateway 地址只在服务端可见。
 *
 * 注意：响应用 `as PageResult<Paper>` 裸断言，无运行时校验。
 * TODO(contract-validation)：API 契约稳定后可用 zod safeParse 替换以防后端字段漂移。
 */
export async function fetchFeed(tab: FeedTab, page = 1, pageSize = 14): Promise<PageResult<Paper>> {
  const res = await gatewayFetch(
    `/patra-catalog/portal/publications?tab=${tab}&page=${page}&pageSize=${pageSize}`,
  );
  if (!res.ok) {
    throw new Error(`文献流加载失败：${res.status}`);
  }
  return (await res.json()) as PageResult<Paper>;
}

/**
 * 在服务端拉取单篇文献详情（按数字 id）。仅 RSC 调用（server-only）。
 *
 * 端点：`GET /patra-catalog/portal/publications/{id}`，响应 `PaperDetail`。
 * 返回约定：
 * - 非正整数 id（坏 URL，含 0 与前导零）→ 直接 `null`，不触达 BE
 * - BE 404（文献不存在）/ 400 / 422（id 被拒）→ `null`，由调用方转 `notFound()`
 * - 其他失败（5xx / 超时 / 网络）→ throw，冒泡到全局 error boundary
 */
export async function fetchPublicationDetail(id: string): Promise<PaperDetail | null> {
  if (!/^[1-9]\d*$/.test(id)) {
    return null;
  }
  const res = await gatewayFetch(`/patra-catalog/portal/publications/${id}`);
  // 404 不存在；400 / 422 是 BE 拒绝了 id（如超出范围），对读者同样是「没有这一页」
  if (res.status === 404 || res.status === 400 || res.status === 422) {
    return null;
  }
  if (!res.ok) {
    throw new Error(`文献详情加载失败：${res.status}`);
  }
  return (await res.json()) as PaperDetail;
}
