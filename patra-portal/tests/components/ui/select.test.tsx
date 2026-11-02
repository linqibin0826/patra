import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const SORTS = [
  { value: "added", label: "加入时间" },
  { value: "published", label: "出版日期" },
  { value: "journal", label: "期刊" },
];

function renderSelect(props: { value?: string | null; onValueChange?: (value: string) => void }) {
  render(
    <Select
      items={SORTS}
      value={props.value ?? null}
      onValueChange={(value) => props.onValueChange?.(value as string)}
    >
      <SelectTrigger aria-label="排序">
        <SelectValue placeholder="请选择" />
      </SelectTrigger>
      <SelectContent>
        {SORTS.map((item) => (
          <SelectItem key={item.value} value={item.value}>
            {item.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>,
  );
  return screen.getByRole("combobox", { name: "排序" });
}

describe("Select", () => {
  it("触发器显示当前选项的文字", () => {
    expect(renderSelect({ value: "journal" })).toHaveTextContent("期刊");
  });

  it("没选时显示占位", () => {
    expect(renderSelect({})).toHaveTextContent("请选择");
  });

  it("点开列出全部选项，当前项已选中", async () => {
    const user = userEvent.setup();
    await user.click(renderSelect({ value: "journal" }));
    const listbox = await screen.findByRole("listbox");
    const options = within(listbox).getAllByRole("option");
    expect(options.map((option) => option.textContent)).toEqual(["加入时间", "出版日期", "期刊"]);
    expect(within(listbox).getByRole("option", { name: "期刊" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("点选项回调新值并收起", async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    await user.click(renderSelect({ value: "journal", onValueChange }));
    await user.click(await screen.findByRole("option", { name: "出版日期" }));
    expect(onValueChange).toHaveBeenCalledWith("published");
    await waitFor(() => expect(screen.queryByRole("listbox")).not.toBeInTheDocument());
  });

  it("键盘：方向键打开、移动，回车选中", async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    const trigger = renderSelect({ value: "added", onValueChange });
    trigger.focus();
    await user.keyboard("{ArrowDown}");
    await screen.findByRole("listbox");
    await user.keyboard("{ArrowDown}{Enter}");
    expect(onValueChange).toHaveBeenCalledWith("published");
  });
});
