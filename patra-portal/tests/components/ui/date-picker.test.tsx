import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { DatePicker } from "@/components/ui/date-picker";
import { Dialog } from "@/components/ui/dialog";

interface Props {
  value?: string;
  reference?: { date: string; label: string };
  disabled?: boolean;
  onChange?: (value: string) => void;
}

function Harness({ value = "", reference, disabled, onChange }: Props) {
  const [current, setCurrent] = useState(value);
  return (
    <>
      <label htmlFor="first">首轮决定日期</label>
      <DatePicker
        id="first"
        value={current}
        onChange={(next) => {
          setCurrent(next);
          onChange?.(next);
        }}
        today="2026-11-02"
        min="1990-01-01"
        reference={reference}
        disabled={disabled}
        sheetTitle="首轮决定日期"
      />
    </>
  );
}

const input = () => screen.getByRole("textbox", { name: "首轮决定日期" });
const openCalendar = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(screen.getByRole("button", { name: "打开日历：首轮决定日期" }));
  return screen.findByRole("grid");
};

describe("DatePicker：直接输入", () => {
  it("常见写法失焦后规范成 YYYY-MM-DD", () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    fireEvent.change(input(), { target: { value: "2026/8/14" } });
    fireEvent.blur(input());
    expect(onChange).toHaveBeenLastCalledWith("2026-08-14");
    expect(input()).toHaveValue("2026-08-14");
  });

  it("认不出的写法原样交给表单，输入框保留原文", () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    fireEvent.change(input(), { target: { value: "2026/9/31" } });
    fireEvent.blur(input());
    expect(onChange).toHaveBeenLastCalledWith("2026/9/31");
    expect(input()).toHaveValue("2026/9/31");
  });

  it("敲完直接回车也会规范化", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    await user.type(input(), "20260814{Enter}");
    expect(onChange).toHaveBeenLastCalledWith("2026-08-14");
  });

  it("禁用时输入框与日历按钮都禁用", () => {
    render(<Harness disabled />);
    expect(input()).toBeDisabled();
    expect(screen.getByRole("button", { name: "打开日历：首轮决定日期" })).toBeDisabled();
  });
});

describe("DatePicker：日历", () => {
  it("打开后标题是年、月两个下拉", async () => {
    const user = userEvent.setup();
    render(<Harness value="2026-09-28" />);
    await openCalendar(user);
    expect(screen.getByRole("combobox", { name: "选择年份" })).toHaveTextContent("2026 年");
    expect(screen.getByRole("combobox", { name: "选择月份" })).toHaveTextContent("9 月");
  });

  it("参照日之前不可选，页脚算出间隔天数", async () => {
    const user = userEvent.setup();
    render(<Harness value="2026-09-28" reference={{ date: "2026-09-15", label: "投稿" }} />);
    const grid = await openCalendar(user);
    expect(within(grid).getByRole("button", { name: /9月14日/ })).toBeDisabled();
    expect(within(grid).getByRole("button", { name: /9月15日/ })).toBeEnabled();
    expect(screen.getByText("距投稿 13 天")).toBeInTheDocument();
  });

  it("选中日早于参照日：页脚不算负天数，日历开在参照日所在月", async () => {
    const user = userEvent.setup();
    render(<Harness value="2026-04-13" reference={{ date: "2026-05-01", label: "投稿" }} />);
    await openCalendar(user);
    expect(screen.getByText("投稿日 05-01 起可选")).toBeInTheDocument();
    expect(screen.queryByText(/距投稿 -/)).not.toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "选择月份" })).toHaveTextContent("5 月");
  });

  it("点日子选中并收起", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness value="2026-09-28" onChange={onChange} />);
    const grid = await openCalendar(user);
    await user.click(within(grid).getByRole("button", { name: /9月30日/ }));
    expect(onChange).toHaveBeenLastCalledWith("2026-09-30");
    await waitFor(() => expect(screen.queryByRole("grid")).not.toBeInTheDocument());
    expect(input()).toHaveValue("2026-09-30");
  });

  it("晚于今天的日子不可选", async () => {
    const user = userEvent.setup();
    render(<Harness value="2026-11-01" />);
    const grid = await openCalendar(user);
    expect(within(grid).getByRole("button", { name: /11月3日/ })).toBeDisabled();
  });

  it("1 月点上个月进入上一年 12 月，年月下拉跟着变", async () => {
    const user = userEvent.setup();
    render(<Harness value="2026-01-10" />);
    await openCalendar(user);
    await user.click(screen.getByRole("button", { name: "上个月" }));
    expect(screen.getByRole("combobox", { name: "选择年份" })).toHaveTextContent("2025 年");
    expect(screen.getByRole("combobox", { name: "选择月份" })).toHaveTextContent("12 月");
  });

  it("年份下拉从今年倒排", async () => {
    const user = userEvent.setup();
    render(<Harness value="2026-03-08" />);
    await openCalendar(user);
    await user.click(screen.getByRole("combobox", { name: "选择年份" }));
    const years = within(await screen.findByRole("listbox")).getAllByRole("option");
    expect(years[0]).toHaveTextContent("2026 年");
    expect(years.at(-1)).toHaveTextContent("1990 年");
  });

  it("用年份下拉跳到 2023 年", async () => {
    const user = userEvent.setup();
    render(<Harness value="2026-03-08" />);
    await openCalendar(user);
    await user.click(screen.getByRole("combobox", { name: "选择年份" }));
    await user.click(await screen.findByRole("option", { name: "2023 年" }));
    expect(screen.getByRole("combobox", { name: "选择年份" })).toHaveTextContent("2023 年");
    expect(screen.getByRole("grid")).toBeInTheDocument();
  });

  it("「今天」选中今天，「清除」清空", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness value="2026-09-28" onChange={onChange} />);
    await openCalendar(user);
    await user.click(screen.getByRole("button", { name: "今天" }));
    expect(onChange).toHaveBeenLastCalledWith("2026-11-02");
    await openCalendar(user);
    await user.click(screen.getByRole("button", { name: "清除" }));
    expect(onChange).toHaveBeenLastCalledWith("");
  });

  it("在对话框里按 Esc 只关日历，不关对话框", async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    render(
      <Dialog open onOpenChange={onOpenChange} title="写投稿经验">
        <Harness value="2026-09-28" />
      </Dialog>,
    );
    await openCalendar(user);
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("grid")).not.toBeInTheDocument());
    expect(onOpenChange).not.toHaveBeenCalled();
  });
});
