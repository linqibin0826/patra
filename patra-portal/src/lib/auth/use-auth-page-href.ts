import { usePathname, useSearchParams } from "next/navigation";
import { authPageHref } from "@/lib/auth/next-path";

/// 导航栏里登录页、注册页的链接：带上当前地址（路径加查询串，不含 `#`）作 `next`。
/// 用了 `useSearchParams`：调用它的组件要包在 Suspense 里，否则静态渲染的页面在构建时报错。
export function useAuthPageHref(page: "/login" | "/register"): string {
  const pathname = usePathname();
  const query = useSearchParams().toString();
  return authPageHref(page, query ? `${pathname}?${query}` : pathname);
}
