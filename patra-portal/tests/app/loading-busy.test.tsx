import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ComponentType } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import JournalsLoading from "@/app/journals/(list)/loading";
import JournalDetailLoading from "@/app/journals/[id]/loading";
import PapersLoading from "@/app/papers/(list)/loading";
import PaperDetailLoading from "@/app/papers/[id]/loading";

vi.mock("next/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next/navigation")>();
  return {
    ...actual,
    usePathname: () => "/",
    useSearchParams: () => new actual.ReadonlyURLSearchParams(),
    useRouter: () => ({ push: vi.fn() }),
  };
});
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("各路由加载骨架", () => {
  it.each<[string, ComponentType]>([
    ["/papers", PapersLoading],
    ["/journals", JournalsLoading],
    ["/papers/[id]", PaperDetailLoading],
    ["/journals/[id]", JournalDetailLoading],
  ])("%s 的 main 标记 aria-busy", (_, Loading) => {
    // 骨架里的导航栏要读当前用户：`/api/auth/me` 回未登录
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ user: null })),
    );
    render(
      <QueryClientProvider client={new QueryClient()}>
        <Loading />
      </QueryClientProvider>,
    );
    expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
  });
});
