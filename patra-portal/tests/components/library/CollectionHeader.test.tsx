import { screen } from "@testing-library/react";
import { makeCollection, makeOverview } from "@tests/fixtures/interaction";
import { renderWithLibrary } from "@tests/support/library";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { CollectionHeader } from "@/components/library/CollectionHeader";

vi.mock("@/lib/interaction/collection-actions", () => ({
  setVisibility: vi.fn(),
  updateCollection: vi.fn(),
  deleteCollection: vi.fn(),
}));
vi.mock("@/lib/identity/verification-actions", () => ({ resendVerificationEmail: vi.fn() }));
vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/library/9",
  useSearchParams: () => new URLSearchParams(),
}));

function renderHeader(visibility: "PRIVATE" | "PUBLIC") {
  return renderWithLibrary(
    <CollectionHeader
      collection={makeCollection({ visibility })}
      context={{ publicLink: "http://mini/collections/9", profileVisibility: "PUBLIC" }}
      onCite={() => {}}
      onExport={() => {}}
    />,
    { overview: makeOverview() },
  );
}

describe("CollectionHeader", () => {
  it("名称、简介、元信息行", () => {
    renderHeader("PRIVATE");
    expect(screen.getByRole("heading", { name: "组会" })).toBeInTheDocument();
    expect(screen.getByText("每周四组会要讲的文献，按主题分好。")).toBeInTheDocument();
    expect(screen.getByText(/私有 · 23 篇 · 更新于 2026 年 10 月 24 日/)).toBeInTheDocument();
  });

  it("私有：菜单只有编辑与删除", async () => {
    const { user } = renderHeader("PRIVATE");
    await user.click(screen.getByRole("button", { name: "收藏夹操作" }));
    await screen.findByRole("menu");
    const names = screen.getAllByRole("menuitem").map((item) => item.textContent);
    expect(names).toContain("编辑名称和简介");
    expect(names).toContain("删除收藏夹");
    expect(names).not.toContain("复制公开链接");
    expect(names).not.toContain("在阅读页查看");
  });

  it("公开：菜单多出复制公开链接与在阅读页查看", async () => {
    const { user } = renderHeader("PUBLIC");
    await user.click(screen.getByRole("button", { name: "收藏夹操作" }));
    await screen.findByRole("menu");
    expect(screen.getByRole("menuitem", { name: "复制公开链接" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "在阅读页查看" })).toHaveAttribute(
      "href",
      "/collections/9",
    );
  });

  it("编辑名称和简介：打开编辑对话框", async () => {
    const { user } = renderHeader("PRIVATE");
    await user.click(screen.getByRole("button", { name: "收藏夹操作" }));
    await user.click(await screen.findByRole("menuitem", { name: "编辑名称和简介" }));
    expect(await screen.findByRole("dialog", { name: "编辑收藏夹" })).toBeInTheDocument();
  });
});
