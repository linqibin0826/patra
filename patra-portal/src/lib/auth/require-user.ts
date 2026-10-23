import "server-only";
import { redirect } from "next/navigation";
import { lookupSession } from "@/lib/auth/current-user";
import { authPageHref } from "@/lib/auth/next-path";
import type { CurrentUser } from "@/lib/auth/types";

/// 设置页的登录守卫（简报 §2.2 规则 1）：已登录返回当前用户；未登录或会话失效去登录页，`next` 是这一页
/// （页面自己知道路径，layout 不知道，所以守卫放在页面里）；网关不可用时抛错交给错误页。
/// `lookupSession` 有 `cache()`：同一次渲染里 layout 再查一次不会多请求网关。
export async function requireUser(path: string): Promise<CurrentUser> {
  const session = await lookupSession();
  if (session.status === "authenticated") {
    return session.user;
  }
  if (session.status === "unavailable") {
    throw new Error("登录状态暂时不可用");
  }
  redirect(authPageHref("/login", path));
}
