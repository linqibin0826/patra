import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { RadioRow } from "@/components/settings/RadioRow";

describe("RadioRow", () => {
  it("标题、说明与可选的小字标记都在单选的可及名里", () => {
    render(
      <>
        <RadioRow
          name="mention"
          title="离站时逐条"
          description="你不在 Patra 上时发一封。"
          badge="默认"
          checked
          onSelect={() => {}}
        />
        <RadioRow
          name="mention"
          title="关"
          description="只在站内提醒，不发邮件。"
          checked={false}
          onSelect={() => {}}
        />
      </>,
    );
    expect(screen.getByRole("radio", { name: /^离站时逐条\s*默认/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: /^关 只在站内提醒/ })).not.toBeChecked();
    expect(screen.getAllByText("默认")).toHaveLength(1);
  });

  it("点它就选它", async () => {
    const onSelect = vi.fn();
    render(
      <RadioRow
        name="reply"
        title="每日汇总"
        description="每天发一封。"
        checked={false}
        onSelect={onSelect}
      />,
    );
    await userEvent.setup().click(screen.getByRole("radio", { name: /每日汇总/ }));
    expect(onSelect).toHaveBeenCalledTimes(1);
  });
});
