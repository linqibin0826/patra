import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import type { ReactNode } from "react";
import { LibraryStatusProvider } from "@/components/library/LibraryStatusProvider";
import type { CurrentUser } from "@/lib/auth/types";
import type { LibraryEntry } from "@/lib/interaction/library";
import type { ItemStatus, LibraryOverview, Page } from "@/lib/interaction/schema";
import { queryKeys } from "@/lib/query-keys";

interface Options {
  /// 当前用户；null 为未登录
  user?: CurrentUser | null;
  /// 预置的「我的状态」（一批）
  statuses?: ItemStatus[];
  /// 预置的文献库概览
  overview?: LibraryOverview;
  /// 预置的文献库条目页
  items?: { view: string; sort: string; page: number; data: Page<LibraryEntry> };
}

/// 渲染收藏相关组件：一个不重试、数据不过期的 QueryClient，预置当前用户、我的状态与概览，外面包好
/// `LibraryStatusProvider`（keys 取 `statuses` 里的 key）
export function renderWithLibrary(ui: ReactNode, options: Options = {}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY } },
  });
  client.setQueryData(queryKeys.currentUser, options.user === undefined ? USER : options.user);
  const keys = (options.statuses ?? []).map((status) => status.key);
  if (options.statuses) {
    client.setQueryData(queryKeys.libraryStatus(keys), options.statuses);
  }
  if (options.overview) {
    client.setQueryData(queryKeys.libraryOverview, options.overview);
  }
  if (options.items) {
    const { view, sort, page, data } = options.items;
    client.setQueryData(queryKeys.libraryItems(view, sort, page), data);
  }
  // 用 wrapper：rerender 时外层的 Provider 保留，组件不重新挂载
  const view = render(ui, {
    wrapper: ({ children }) => (
      <QueryClientProvider client={client}>
        <LibraryStatusProvider keys={keys}>{children}</LibraryStatusProvider>
      </QueryClientProvider>
    ),
  });
  return { ...view, client, user: userEvent.setup() };
}
