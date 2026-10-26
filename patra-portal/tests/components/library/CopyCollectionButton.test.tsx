import { screen, waitFor } from "@testing-library/react";
import { makeCopyResult } from "@tests/fixtures/interaction";
import { renderWithQuery } from "@tests/support/render-with-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { CopyCollectionButton } from "@/components/library/CopyCollectionButton";
import { copyCollection } from "@/lib/interaction/collection-actions";

vi.mock("@/lib/interaction/collection-actions", () => ({ copyCollection: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

const copy = vi.mocked(copyCollection);

beforeEach(() => {
  copy.mockReset();
  vi.mocked(toast.success).mockReset();
});

async function click() {
  renderWithQuery(<CopyCollectionButton collectionId="9" />);
  await userEvent.setup().click(screen.getByRole("button", { name: "复制为我的收藏夹" }));
}

describe("CopyCollectionButton", () => {
  it("复制中…，成功 toast 写实际名称与篇数，带「去看看」", async () => {
    copy.mockResolvedValue({ ok: true, result: makeCopyResult() });
    await click();
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(
        "已复制为「组会 (2)」：新收进 18 篇，5 篇原本就在文献库",
        expect.objectContaining({ action: expect.objectContaining({ label: "去看看" }) }),
      ),
    );
    expect(screen.getByRole("button", { name: "复制为我的收藏夹" })).toBeInTheDocument();
  });

  it("原本都不在库里：省掉后半句；有已失效的：末尾说明", async () => {
    copy.mockResolvedValue({
      ok: true,
      result: makeCopyResult({ alreadyInLibrary: 0, skippedUnavailable: 2 }),
    });
    await click();
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(
        "已复制为「组会 (2)」：新收进 18 篇，2 篇已失效没有复制",
        expect.anything(),
      ),
    );
  });

  it.each([
    ["collection-limit", "你已经有 200 个收藏夹，先删除一些再复制。"],
    ["library-full", "文献库已满 20,000 篇，复制不了这个收藏夹。先移除一些文献再试。"],
    ["not-visible", "这个收藏夹已经不公开了。"],
    ["unavailable", "没有复制成功，请稍后再试"],
  ] as const)("%s：提示条「%s」", async (reason, text) => {
    copy.mockResolvedValue({ ok: false, reason });
    await click();
    expect(await screen.findByText(text)).toBeInTheDocument();
  });
});
