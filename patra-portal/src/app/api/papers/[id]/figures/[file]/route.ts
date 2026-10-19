import {
  isFigureFileName,
  isPublicationId,
  proxyFullTextFile,
} from "@/lib/portal-api/fulltext-files";

export const dynamic = "force-dynamic";

/** 正文图片：浏览器 → 门户 → 网关 → catalog，类型与缓存头（immutable）由 catalog 设置。 */
export async function GET(request: Request, ctx: RouteContext<"/api/papers/[id]/figures/[file]">) {
  const { id, file } = await ctx.params;
  if (!isPublicationId(id) || !isFigureFileName(file)) {
    return new Response(null, { status: 404 });
  }
  return proxyFullTextFile(
    `/patra-catalog/portal/publications/${id}/fulltext/figures/${file}`,
    request,
  );
}
