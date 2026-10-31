import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import type { ReactElement } from "react";
import { vi } from "vitest";
import type { CurrentUser } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

/// 提醒相关组件与 hook 的渲染：门户默认的缓存设置（60 秒不过期、聚焦不重取），失败不重试；当前用户默认是 USER，
/// 传 null 是未登录。`fetch` 由调用方 `vi.stubGlobal` 装桩
export function renderNotifications(
  ui: ReactElement,
  options: { user?: CurrentUser | null; seed?: (client: QueryClient) => void } = {},
) {
  const client = new QueryClient({
    defaultOptions: { queries: { staleTime: 60_000, refetchOnWindowFocus: false, retry: false } },
  });
  client.setQueryData(queryKeys.currentUser, options.user === undefined ? USER : options.user);
  options.seed?.(client);
  const wrap = (node: ReactElement) => (
    <QueryClientProvider client={client}>{node}</QueryClientProvider>
  );
  const view = render(wrap(ui));
  return { ...view, client, rerender: (next: ReactElement = ui) => view.rerender(wrap(next)) };
}

/// 未读数接口的响应：200 带 ETag，或 304
export function unreadResponse(
  counts: { total: number; replies: number; mentions: number; helpful: number } | 304,
  etag = '"x"',
): Response {
  return counts === 304
    ? new Response(null, { status: 304, headers: { ETag: etag } })
    : Response.json(counts, { headers: { ETag: etag } });
}

/// 等到 `fetch` 被调了 n 次
export async function fetchCalls(fetchMock: ReturnType<typeof vi.fn>, n: number) {
  await vi.waitFor(() => {
    if (fetchMock.mock.calls.length < n) {
      throw new Error(`fetch 只调了 ${fetchMock.mock.calls.length} 次`);
    }
  });
}
