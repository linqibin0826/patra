import type { NextRequest } from "next/server";
import { callNotification } from "@/lib/notification/request";

/// 一键退订地址（PAP-142 第 7、8.6 节，PAP-148 spec 第 6 节）：写在已经发出去的邮件的 `List-Unsubscribe` 头里，
/// 路径与参数名是长期契约。网关不对公网，邮箱服务商的请求只能打到门户，由这里转给网关。
/// 不查同源、不读 Cookie：请求来自邮箱服务商的服务器，没有 `Origin`；能不能退订只看地址里的令牌，操作只会把邮件
/// 关掉、重复请求结果相同（spec 第 2.2 节）。不打 `request.url`：查询串里有令牌。

const NO_STORE = "no-store";

/// 地址里的令牌与类别（各取第一个，缺了就不带），别的参数不转
function linkParams(request: NextRequest): URLSearchParams {
  const params = new URLSearchParams();
  for (const name of ["token", "category"]) {
    const value = request.nextUrl.searchParams.get(name);
    if (value !== null) {
      params.set(name, value);
    }
  }
  return params;
}

/// RFC 8058 一键退订：邮箱服务商 POST 过来（请求体 `List-Unsubscribe=One-Click`，后端不看，门户也不读也不转）。
/// 转给网关，不带会话令牌、不跟随重定向；把网关的状态码原样交回去，空响应体。网关连不上是 502。
export async function POST(request: NextRequest): Promise<Response> {
  const reply = await callNotification(
    "/unsubscribe/one-click",
    { method: "POST" },
    { auth: "none", query: linkParams(request) },
  );
  const status =
    reply.kind === "ok" ? reply.res.status : reply.kind === "problem" ? reply.problem.status : 502;
  return new Response(null, { status, headers: { "Cache-Control": NO_STORE } });
}

/// 不支持一键退订的邮件客户端会在浏览器里打开这个地址：跳到确认页（令牌与类别放进片段），不改任何东西。
/// 用相对地址：门户在 Cloudflare Tunnel 后面，`request.url` 的主机不一定是公网域名。
export function GET(request: NextRequest): Response {
  const fragment = linkParams(request).toString();
  return new Response(null, {
    status: 303,
    headers: {
      Location: fragment ? `/unsubscribe#${fragment}` : "/unsubscribe",
      "Referrer-Policy": "no-referrer",
      "Cache-Control": NO_STORE,
    },
  });
}
