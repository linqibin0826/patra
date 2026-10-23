import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import {
  Combobox,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxPopup,
} from "@/components/ui/combobox";

const ITEMS = ["Heart Failure", "Hypertension"];

function Harness({ onValueChange }: { onValueChange: (value: string | null) => void }) {
  return (
    <Combobox items={ITEMS} filter={null} onValueChange={onValueChange}>
      <ComboboxInput aria-label="搜索" />
      <ComboboxPopup>
        <ComboboxList>
          {(item: string) => (
            <ComboboxItem key={item} value={item}>
              {item}
            </ComboboxItem>
          )}
        </ComboboxList>
      </ComboboxPopup>
    </Combobox>
  );
}

describe("Combobox", () => {
  it("输入框是 combobox，打开后方向键 + 回车选中", async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    render(<Harness onValueChange={onValueChange} />);
    const input = screen.getByRole("combobox", { name: "搜索" });
    expect(input).toHaveAttribute("aria-expanded", "false");
    await user.click(input);
    await user.type(input, "h");
    expect(input).toHaveAttribute("aria-expanded", "true");
    expect(await screen.findByRole("listbox")).toBeInTheDocument();
    await user.keyboard("{ArrowDown}{Enter}");
    expect(onValueChange).toHaveBeenCalledWith("Heart Failure", expect.anything());
  });
});
