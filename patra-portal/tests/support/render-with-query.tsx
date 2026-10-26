import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type RenderOptions, render } from "@testing-library/react";
import {
  AppRouterContext,
  type AppRouterInstance,
} from "next/dist/shared/lib/app-router-context.shared-runtime";
import type { ReactElement } from "react";
import { vi } from "vitest";
import { queryKeys } from "@/lib/query-keys";

/// 不跳转的路由桩：组件里的 `useRouter()` 需要 App Router 的上下文
const ROUTER: AppRouterInstance = {
  bfcacheId: "test",
  back: vi.fn(),
  forward: vi.fn(),
  refresh: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
  prefetch: vi.fn(),
};

/// 渲染里面带收藏按钮的组件（卡片、列表项、页头）：给一个不重试的 QueryClient 与路由桩，当前用户预置为
/// 未登录，收藏按钮显示为 `save.anon`，不发请求
export function renderWithQuery(ui: ReactElement, options?: RenderOptions) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY } },
  });
  client.setQueryData(queryKeys.currentUser, null);
  return render(
    <AppRouterContext.Provider value={ROUTER}>
      <QueryClientProvider client={client}>{ui}</QueryClientProvider>
    </AppRouterContext.Provider>,
    options,
  );
}
