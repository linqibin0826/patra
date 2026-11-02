import { readHandlerFacts, requestPage } from "@/lib/analytics/server/context";
import { trackServerEvent } from "@/lib/analytics/server/send";
import { isPublicationId, proxyFullTextFile } from "@/lib/portal-api/fulltext-files";

export const dynamic = "force-dynamic";

/** 原版 PDF 下载：浏览器 → 门户 → 网关 → catalog（MinIO 流式读出），文件名与缓存头由 catalog 设置。
 *  开始回传文件（200）时记一次 PDF Downloaded，也算一次读过全文（埋点方案 §6.3）；304 等不记 */
export async function GET(request: Request, ctx: RouteContext<"/api/papers/[id]/pdf">) {
  const { id } = await ctx.params;
  if (!isPublicationId(id)) {
    return new Response(null, { status: 404 });
  }
  const res = await proxyFullTextFile(
    `/patra-catalog/portal/publications/${id}/fulltext/pdf`,
    request,
  );
  if (res.status === 200) {
    trackServerEvent(
      { name: "pdf_downloaded", properties: { article_id: id } },
      await readHandlerFacts(),
      requestPage(request),
    );
  }
  return res;
}
