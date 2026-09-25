import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { JournalResults } from "@/components/portal/journals/JournalResults";
import { JournalsQueryProvider } from "@/components/portal/journals/JournalsQueryProvider";
import type { VenueBrowsePage, VenueBrowseQuery } from "@/types/portal";

vi.mock("@/lib/portal-api/venues", () => ({
  fetchVenuesPage: vi.fn(),
  fetchVenuesFacets: vi.fn(),
  fetchVenues: vi.fn(),
  fetchVenueDetail: vi.fn(),
}));

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

import { fetchVenuesPage } from "@/lib/portal-api/venues";

const BASE_QUERY: VenueBrowseQuery = {
  q: "",
  sort: "if",
  page: 1,
  subject: [],
  jcr: [],
  cas: [],
  casTop: false,
  oa: false,
  doaj: false,
  country: [],
};

const EMPTY_PAGE: VenueBrowsePage = {
  page: 1,
  pageSize: 12,
  total: 0,
  totalPages: 0,
  items: [],
};

const NON_EMPTY_PAGE: VenueBrowsePage = {
  page: 1,
  pageSize: 12,
  total: 2,
  totalPages: 1,
  items: [
    {
      id: "1",
      name: "Nature",
      abbr: "Nat",
      coverObjectKey: null,
      impactFactor: 69.0,
      jcrQuartile: "Q1",
      jcrSubject: null,
      casMajorCategory: null,
      casMajorQuartile: null,
      casIsTop: null,
      countryCode: null,
      citedByCount: null,
      foundedYear: 1869,
      isOpenAccess: null,
      isInDoaj: null,
      issnL: null,
    },
    {
      id: "2",
      name: "Science",
      abbr: "Sci",
      coverObjectKey: null,
      impactFactor: 56.0,
      jcrQuartile: "Q1",
      jcrSubject: null,
      casMajorCategory: null,
      casMajorQuartile: null,
      casIsTop: null,
      countryCode: null,
      citedByCount: null,
      foundedYear: 1880,
      isOpenAccess: null,
      isInDoaj: null,
      issnL: null,
    },
  ],
};

async function renderResults(query: VenueBrowseQuery) {
  const ui = await JournalResults({ query });
  return render(<JournalsQueryProvider query={query}>{ui}</JournalsQueryProvider>);
}

describe("JournalResults", () => {
  beforeEach(() => {
    vi.mocked(fetchVenuesPage).mockReset();
  });

  it("total=0 且无筛选 → 渲染 empty-library 空态", async () => {
    vi.mocked(fetchVenuesPage).mockResolvedValue(EMPTY_PAGE);
    await renderResults(BASE_QUERY);
    expect(screen.getByRole("heading", { name: /库中暂无期刊/ })).toBeInTheDocument();
  });

  it("total=0 且有 q 筛选 → 渲染 no-results 空态", async () => {
    vi.mocked(fetchVenuesPage).mockResolvedValue(EMPTY_PAGE);
    await renderResults({ ...BASE_QUERY, q: "nature" });
    expect(screen.getByRole("heading", { name: /nature/ })).toBeInTheDocument();
  });

  it("total=0 且有 subject 筛选 → 渲染 no-results 空态", async () => {
    vi.mocked(fetchVenuesPage).mockResolvedValue(EMPTY_PAGE);
    await renderResults({ ...BASE_QUERY, subject: ["Medicine"] });
    expect(screen.getByRole("heading", { name: /未找到匹配的期刊/ })).toBeInTheDocument();
  });

  it("total>0 → 渲染期刊 grid 中的期刊名", async () => {
    vi.mocked(fetchVenuesPage).mockResolvedValue(NON_EMPTY_PAGE);
    await renderResults(BASE_QUERY);
    expect(screen.getByText("Nature")).toBeInTheDocument();
    expect(screen.getByText("Science")).toBeInTheDocument();
  });

  it("total>0 → 渲染命中数文案「共 N 本期刊」", async () => {
    vi.mocked(fetchVenuesPage).mockResolvedValue(NON_EMPTY_PAGE);
    await renderResults(BASE_QUERY);
    expect(screen.getByText(/共 2 本期刊/)).toBeInTheDocument();
  });

  it("多页时渲染公用分页，链接指向 /journals", async () => {
    vi.mocked(fetchVenuesPage).mockResolvedValue({ ...NON_EMPTY_PAGE, total: 30, totalPages: 3 });
    await renderResults(BASE_QUERY);
    expect(screen.getByRole("link", { name: "2" })).toHaveAttribute("href", "/journals?page=2");
  });
});
