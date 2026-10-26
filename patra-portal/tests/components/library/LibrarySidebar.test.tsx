import { screen } from "@testing-library/react";
import { makeCollection, makeOverview } from "@tests/fixtures/interaction";
import { renderWithLibrary } from "@tests/support/library";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { LibrarySidebar } from "@/components/library/LibrarySidebar";

let pathname = "/library";
vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  usePathname: () => pathname,
  useRouter: () => ({ push: vi.fn() }),
}));

const OVERVIEW = makeOverview({
  total: 1284,
  unfiled: 312,
  collections: [
    makeCollection({ collectionId: "9", name: "组会", itemCount: 23 }),
    makeCollection({ collectionId: "10", name: "心衰综述", visibility: "PUBLIC", itemCount: 156 }),
  ],
});

describe("LibrarySidebar", () => {
  it("全部 / 未分类的篇数，收藏夹带可见性与篇数", () => {
    renderWithLibrary(<LibrarySidebar onCreate={() => {}} />, { overview: OVERVIEW });
    expect(screen.getByRole("link", { name: /全部\s*1,284/ })).toHaveAttribute("href", "/library");
    expect(screen.getByRole("link", { name: /未分类\s*312/ })).toHaveAttribute(
      "href",
      "/library/unfiled",
    );
    expect(screen.getByRole("link", { name: /组会.*私有.*23/ })).toHaveAttribute(
      "href",
      "/library/9",
    );
    expect(screen.getByRole("link", { name: /心衰综述.*公开.*156/ })).toBeInTheDocument();
  });

  it("当前视图 aria-current=page", () => {
    pathname = "/library/9";
    renderWithLibrary(<LibrarySidebar onCreate={() => {}} />, { overview: OVERVIEW });
    expect(screen.getByRole("link", { name: /组会/ })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: /全部/ })).not.toHaveAttribute("aria-current");
    pathname = "/library";
  });

  it("「+」新建收藏夹", async () => {
    const onCreate = vi.fn();
    const { user } = renderWithLibrary(<LibrarySidebar onCreate={onCreate} />, {
      overview: OVERVIEW,
    });
    await user.click(screen.getByRole("button", { name: "新建收藏夹" }));
    expect(onCreate).toHaveBeenCalled();
  });

  it("没有收藏夹：提示与新建按钮", () => {
    renderWithLibrary(<LibrarySidebar onCreate={() => {}} />, {
      overview: makeOverview({ collections: [] }),
    });
    expect(screen.getByText("还没有收藏夹")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "新建收藏夹" })).toHaveLength(2);
  });

  it("已有 200 个：新建禁用", () => {
    renderWithLibrary(<LibrarySidebar onCreate={() => {}} />, {
      overview: makeOverview({
        collections: Array.from({ length: 200 }, (_, i) =>
          makeCollection({ collectionId: String(i + 1), name: `夹 ${i + 1}` }),
        ),
      }),
    });
    expect(screen.getByRole("button", { name: "新建收藏夹" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });
});

describe("LibrarySidebar · 手机视图切换抽屉", () => {
  it("可见性用文字标在名称前", () => {
    renderWithLibrary(<LibrarySidebar onCreate={() => {}} visibilityText />, {
      overview: OVERVIEW,
    });
    expect(screen.getByRole("link", { name: /私有\s*组会/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /公开\s*心衰综述/ })).toBeInTheDocument();
  });
});
