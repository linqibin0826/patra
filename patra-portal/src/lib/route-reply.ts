import "server-only";
import { NextResponse } from "next/server";
import type { Read } from "@/lib/interaction/read";

/// 用户态数据不缓存
export const NO_STORE = { "Cache-Control": "private, no-store" };

/// 一次读的结果 → Route Handler 的响应：ok 200；没有会话 401；不存在 404；其余 502
export function readReply<T>(read: Read<T>): NextResponse {
  switch (read.kind) {
    case "ok":
      return NextResponse.json(read.data, { headers: NO_STORE });
    case "signed-out":
      return NextResponse.json({ reason: "signed-out" }, { status: 401, headers: NO_STORE });
    case "not-found":
      return NextResponse.json({ reason: "not-found" }, { status: 404, headers: NO_STORE });
    case "unavailable":
      return NextResponse.json({ reason: "unavailable" }, { status: 502, headers: NO_STORE });
  }
}

/// 请求体不合格
export function invalidRequest(): NextResponse {
  return NextResponse.json({ reason: "invalid" }, { status: 422, headers: NO_STORE });
}

/// 跨站请求
export function forbidden(): NextResponse {
  return NextResponse.json({ reason: "forbidden" }, { status: 403, headers: NO_STORE });
}

/// 读 JSON 请求体，不是 JSON 时为 undefined
export async function readBody(request: Request): Promise<unknown> {
  try {
    return await request.json();
  } catch {
    return undefined;
  }
}
