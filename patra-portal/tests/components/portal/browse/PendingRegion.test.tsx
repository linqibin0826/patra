import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import {
  type BrowseQueryBase,
  BrowseQueryContext,
  type BrowseQueryContextValue,
  BrowseQueryProvider,
} from "@/components/portal/browse/BrowseQueryProvider";
import { PendingRegion } from "@/components/portal/browse/PendingRegion";

const mockPush = vi.fn<(url: string) => void>();
const mockReplace = vi.fn<(url: string) => void>();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
}));

interface TestQuery {
  q: string;
  tags: string[];
  page: number;
}

const EMPTY: TestQuery = { q: "", tags: [], page: 1 };

function serialize(query: TestQuery): string {
  const params = new URLSearchParams();
  if (query.q) params.set("q", query.q);
  for (const tag of query.tags) params.append("tag", tag);
  if (query.page > 1) params.set("page", String(query.page));
  return params.toString();
}

function withProvider(ui: ReactNode, query: TestQuery = EMPTY) {
  return (
    <BrowseQueryProvider query={query} basePath="/items" serialize={serialize}>
      {ui}
    </BrowseQueryProvider>
  );
}

/** 以固定 context 值渲染（模拟导航进行中）。 */
function withPending(ui: ReactNode, query: TestQuery = EMPTY) {
  const value: BrowseQueryContextValue<BrowseQueryBase> = {
    query,
    isPending: true,
    hrefFor: (q) => `/items?${serialize(q as TestQuery)}`,
    navigate: () => undefined,
  };
  return <BrowseQueryContext.Provider value={value}>{ui}</BrowseQueryContext.Provider>;
}

describe("PendingRegion", () => {
  it("空闲时不标记 aria-busy", () => {
    render(withProvider(<PendingRegion>结果</PendingRegion>));
    expect(screen.getByText("结果")).not.toHaveAttribute("aria-busy");
  });

  it("导航进行中：aria-busy=true 且变淡", () => {
    render(withPending(<PendingRegion>结果</PendingRegion>));
    const region = screen.getByText("结果");
    expect(region).toHaveAttribute("aria-busy", "true");
    expect(region.className).toMatch(/opacity-60/);
  });
});
