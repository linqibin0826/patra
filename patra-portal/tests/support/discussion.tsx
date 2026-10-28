import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import {
  AppRouterContext,
  type AppRouterInstance,
} from "next/dist/shared/lib/app-router-context.shared-runtime";
import type { ReactElement } from "react";
import { vi } from "vitest";
import type { CurrentUser } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

/// 讨论区组件的渲染：QueryClient（不重试）+ 路由桩；当前用户默认是已验证邮箱的 USER，传 null 是未登录
export function renderDiscussion(
  ui: ReactElement,
  options: { user?: CurrentUser | null; seed?: (client: QueryClient) => void } = {},
) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY } },
  });
  client.setQueryData(
    queryKeys.currentUser,
    options.user === undefined ? { ...USER, emailVerified: true } : options.user,
  );
  options.seed?.(client);
  const router: AppRouterInstance = {
    bfcacheId: "test",
    back: vi.fn(),
    forward: vi.fn(),
    refresh: vi.fn(),
    push: vi.fn(),
    replace: vi.fn(),
    prefetch: vi.fn(),
  };
  const wrap = (node: ReactElement) => (
    <AppRouterContext.Provider value={router}>
      <QueryClientProvider client={client}>{node}</QueryClientProvider>
    </AppRouterContext.Provider>
  );
  const view = render(wrap(ui));
  /// 换了地址参数（mock 的 useSearchParams）之后重新渲染，保留同一个 QueryClient
  const rerender = (next: ReactElement = ui) => view.rerender(wrap(next));
  return { ...view, rerender, client, router };
}
