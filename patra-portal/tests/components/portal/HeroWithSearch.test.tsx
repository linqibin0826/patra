import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { HeroWithSearch } from "@/components/portal/HeroWithSearch";

const mockPush = vi.fn<(url: string) => void>();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: mockPush }) }));
vi.mock("sonner", () => ({ toast: vi.fn() }));

import { toast } from "sonner";

async function search(tab: string | null, text: string) {
  const user = userEvent.setup();
  render(<HeroWithSearch />);
  if (tab) await user.click(screen.getByRole("tab", { name: tab }));
  if (text) await user.type(screen.getByRole("textbox"), text);
  await user.click(screen.getByRole("button", { name: /搜索/ }));
}

describe("HeroWithSearch", () => {
  beforeEach(() => {
    mockPush.mockClear();
    vi.mocked(toast).mockClear();
  });

  it("关键词 → /papers?q=", async () => {
    await search(null, "GLP-1");
    expect(mockPush).toHaveBeenCalledWith("/papers?q=GLP-1");
  });

  it("作者 → /papers?author=", async () => {
    await search("作者", "Topol");
    expect(mockPush).toHaveBeenLastCalledWith("/papers?author=Topol");
  });

  it("PMID 数字 → /papers?pmid=（命中直跳由 /papers 处理）", async () => {
    await search("PMID", "38491203");
    expect(mockPush).toHaveBeenCalledWith("/papers?pmid=38491203");
  });

  it("DOI → /papers?doi=", async () => {
    await search("DOI", "10.1016/j.x");
    expect(mockPush).toHaveBeenCalledWith("/papers?doi=10.1016%2Fj.x");
  });

  it("PMID 非数字：提示且不跳转", async () => {
    await search("PMID", "abc");
    expect(toast).toHaveBeenCalledWith("PMID 应为纯数字");
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("空内容不跳转、不提示", async () => {
    await search(null, "");
    expect(mockPush).not.toHaveBeenCalled();
    expect(toast).not.toHaveBeenCalled();
  });
});
