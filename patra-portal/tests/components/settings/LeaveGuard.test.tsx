import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { LeaveGuard } from "@/components/settings/LeaveGuard";

const router = { push: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/settings/profile",
}));

function renderGuard(when = true) {
  render(
    <>
      <LeaveGuard when={when} />
      <a href="/papers">文献</a>
      <a href="/papers" target="_blank" rel="noopener">
        新标签页
      </a>
      <a href="https://example.org/x">外站</a>
      <a href="/settings/profile#avatar">本页锚点</a>
    </>,
  );
  return userEvent.setup();
}

afterEach(() => vi.clearAllMocks());

describe("LeaveGuard", () => {
  it("有未保存的修改时点站内链接：不跳，弹确认", async () => {
    const user = renderGuard();
    await user.click(screen.getByRole("link", { name: "文献" }));
    expect(
      await screen.findByRole("dialog", { name: "有未保存的修改" }),
    ).toHaveAccessibleDescription("确定离开吗？离开后这次的修改不会保存。");
    expect(router.push).not.toHaveBeenCalled();
  });

  it("选「离开」：跳过去", async () => {
    const user = renderGuard();
    await user.click(screen.getByRole("link", { name: "文献" }));
    await user.click(await screen.findByRole("button", { name: "离开" }));
    expect(router.push).toHaveBeenCalledWith("/papers");
  });

  it("选「继续编辑」：关掉，不跳", async () => {
    const user = renderGuard();
    await user.click(screen.getByRole("link", { name: "文献" }));
    await user.click(await screen.findByRole("button", { name: "继续编辑" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(router.push).not.toHaveBeenCalled();
  });

  it.each([
    ["新标签页", {}],
    ["外站", {}],
    ["本页锚点", {}],
    ["文献", { metaKey: true }],
    ["文献", { ctrlKey: true }],
    ["文献", { shiftKey: true }],
    ["文献", { button: 1 }],
  ])("「%s」%j：不拦", (name, modifiers) => {
    renderGuard();
    const link = screen.getByRole("link", { name });
    const event = new MouseEvent("click", { bubbles: true, cancelable: true, ...modifiers });
    link.addEventListener("click", (e) => e.preventDefault());
    link.dispatchEvent(event);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("没有未保存的修改：不拦", () => {
    renderGuard(false);
    const link = screen.getByRole("link", { name: "文献" });
    link.addEventListener("click", (e) => e.preventDefault());
    fireEvent.click(link);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("关标签页、刷新：有修改时拦下 beforeunload", () => {
    renderGuard();
    const event = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
  });

  it("没有修改时不拦 beforeunload", () => {
    renderGuard(false);
    const event = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(false);
  });
});
