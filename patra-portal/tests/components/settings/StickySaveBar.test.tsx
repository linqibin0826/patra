import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { StickySaveBar } from "@/components/settings/StickySaveBar";

describe("StickySaveBar", () => {
  it("放弃、保存两个按钮", async () => {
    const onDiscard = vi.fn();
    const user = userEvent.setup();
    render(<StickySaveBar saving={false} onDiscard={onDiscard} />);
    expect(screen.getByRole("region", { name: "保存资料" })).toHaveTextContent("有未保存的修改");
    await user.click(screen.getByRole("button", { name: "放弃" }));
    expect(onDiscard).toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "保存" })).toHaveAttribute("type", "submit");
  });

  it("保存中：两个按钮都不能点", () => {
    render(<StickySaveBar saving onDiscard={vi.fn()} />);
    expect(screen.getByRole("button", { name: "保存中…" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByRole("button", { name: "放弃" })).toHaveAttribute("aria-disabled", "true");
  });
});
