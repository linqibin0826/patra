import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { makeItemStatus } from "@tests/fixtures/interaction";
import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { LibraryStatusProvider, useSaveStatus } from "@/components/library/LibraryStatusProvider";
import type { CurrentUser } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

afterEach(() => {
  vi.unstubAllGlobals();
});

function Probe({ id }: { id: string }) {
  const status = useSaveStatus(id);
  return (
    <span data-testid={`s-${id}`}>
      {status.kind === "ready" ? (status.status.collected ? "collected" : "idle") : status.kind}
    </span>
  );
}

function renderScope(user: CurrentUser | null | undefined, keys: string[], probes: string[]) {
  const client = new QueryClient({ defaultOptions: { queries: { staleTime: 60_000 } } });
  if (user !== undefined) {
    client.setQueryData(queryKeys.currentUser, user);
  }
  render(
    <QueryClientProvider client={client}>
      <LibraryStatusProvider keys={keys}>
        {probes.map((id, index) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: 测试里同一个 key 渲染两次
          <Probe key={index} id={id} />
        ))}
      </LibraryStatusProvider>
    </QueryClientProvider>,
  );
  return client;
}

describe("LibraryStatusProvider", () => {
  it("未登录：anon，不请求", () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    renderScope(null, ["11"], ["11"]);
    expect(screen.getByTestId("s-11")).toHaveTextContent("anon");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("登录状态还没确认：pending", () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise(() => {})),
    );
    renderScope(undefined, ["11"], ["11"]);
    expect(screen.getByTestId("s-11")).toHaveTextContent("pending");
  });

  it("dedupes keys and shares status：一屏一次批量请求，同一篇的两个按钮拿同一份状态", async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({
        items: [makeItemStatus({ key: "11", collected: true }), makeItemStatus({ key: "12" })],
      }),
    );
    vi.stubGlobal("fetch", fetchMock);
    renderScope(USER, ["11", "11", "12"], ["11", "11", "12"]);
    await waitFor(() => expect(screen.getAllByTestId("s-11")[0]).toHaveTextContent("collected"));
    expect(screen.getAllByTestId("s-11")[1]).toHaveTextContent("collected");
    expect(screen.getByTestId("s-12")).toHaveTextContent("idle");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/me/library/status");
    expect(JSON.parse(String(init.body))).toEqual({ keys: ["11", "12"] });
  });

  it("请求失败：unavailable，不重试", async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 502 }));
    vi.stubGlobal("fetch", fetchMock);
    renderScope(USER, ["11"], ["11"]);
    await waitFor(() => expect(screen.getByTestId("s-11")).toHaveTextContent("unavailable"));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
