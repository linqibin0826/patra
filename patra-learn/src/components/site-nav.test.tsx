// @vitest-environment jsdom
// 顶栏的「批注」入口：链到 /notes，有批注时带条数
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ANNOTATIONS_KEY } from "@/lib/annotations/annotations";
import { render, screen } from "@/teaching/testing/render";
import { SiteNav } from "./site-nav";

vi.mock("next/navigation", () => ({
  usePathname: () => "/notes",
  useRouter: () => ({ push: vi.fn() }),
}));

const quote = { exact: "一句话", prefix: "", suffix: "" };

beforeEach(() => window.localStorage.clear());

describe("顶栏「批注」入口", () => {
  it("链到 /notes，在批注页时标成当前页", () => {
    render(<SiteNav />);
    const link = screen.getByRole("link", { name: /^批注/ });
    expect(link.getAttribute("href")).toBe("/notes");
    expect(link.getAttribute("aria-current")).toBe("page");
  });

  it("有批注时显示条数", () => {
    window.localStorage.setItem(
      ANNOTATIONS_KEY,
      JSON.stringify(
        ["a", "b", "c"].map((id) => ({
          id,
          slug: "x",
          kind: "note",
          text: "",
          quote,
          createdAt: 1,
        })),
      ),
    );
    render(<SiteNav />);
    expect(screen.getByRole("link", { name: "批注，3 条" })).toBeTruthy();
  });
});
