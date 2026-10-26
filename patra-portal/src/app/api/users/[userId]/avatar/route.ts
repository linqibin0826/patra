import { readClientOrigin } from "@/lib/client-origin";
import { proxyGatewayFile } from "@/lib/portal-api/gateway-file";

export const dynamic = "force-dynamic";

/// 头像透传的响应头：类型、长度、`ETag` 与不可变缓存头由 identity 设置
const PASSTHROUGH_HEADERS = ["Content-Type", "Content-Length", "ETag", "Cache-Control"];

/// Snowflake 用户 ID：正整数、无前导零，最多 19 位
const USER_ID = /^[1-9][0-9]{0,18}$/;
const VERSION = /^[0-9a-f]{16}$/;
const SIZES = new Set(["128", "512"]);

/// 头像：浏览器 → 门户 → 网关 → identity（v0.12 决策 G；v0.13 决策 E 改按用户 ID，改名后地址不失效）。用户 ID、版本号、尺寸三个参数先按固定形状
/// 校验，不合格直接 404、不打网关，也不把任意查询串转给网关。
export async function GET(request: Request, ctx: RouteContext<"/api/users/[userId]/avatar">) {
  const { userId } = await ctx.params;
  const query = new URL(request.url).searchParams;
  const version = query.get("v") ?? "";
  const size = query.get("size") ?? "";
  if (!USER_ID.test(userId) || !VERSION.test(version) || !SIZES.has(size)) {
    return new Response(null, { status: 404 });
  }
  return proxyGatewayFile(
    `/patra-identity/users/${userId}/avatar?size=${size}&v=${version}`,
    request,
    { passthrough: PASSTHROUGH_HEADERS, client: await readClientOrigin() },
  );
}
