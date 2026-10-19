import { isPublicationId, proxyFullTextFile } from "@/lib/portal-api/fulltext-files";

export const dynamic = "force-dynamic";

/** 原版 PDF 下载：浏览器 → 门户 → 网关 → catalog（MinIO 流式读出），文件名与缓存头由 catalog 设置。 */
export async function GET(request: Request, ctx: RouteContext<"/api/papers/[id]/pdf">) {
  const { id } = await ctx.params;
  if (!isPublicationId(id)) {
    return new Response(null, { status: 404 });
  }
  return proxyFullTextFile(`/patra-catalog/portal/publications/${id}/fulltext/pdf`, request);
}
