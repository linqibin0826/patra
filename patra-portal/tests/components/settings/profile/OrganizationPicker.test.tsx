import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { OrganizationPicker } from "@/components/settings/profile/OrganizationPicker";
import type { OrganizationValue } from "@/lib/identity/profile-form";

const PUMCH = {
  rorId: "04jztag35",
  displayName: "Peking Union Medical College Hospital",
  zhName: "北京协和医院",
  matchedName: "PUMCH",
  cityName: "Beijing",
  countryName: "中国",
  types: ["healthcare"],
};

const fetchMock = vi.fn();
const onChange = vi.fn();

function Harness({ initial }: { initial: OrganizationValue | null }) {
  const [value, setValue] = useState(initial);
  return (
    <OrganizationPicker
      id="org"
      value={value}
      onChange={(next) => {
        onChange(next);
        setValue(next);
      }}
    />
  );
}

function renderPicker(initial: OrganizationValue | null = null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <Harness initial={initial} />
    </QueryClientProvider>,
  );
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
}

/// 输入后等过防抖与请求
async function typeAndSettle(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.type(screen.getByRole("combobox"), text);
  await act(async () => {
    await vi.advanceTimersByTimeAsync(300);
  });
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockImplementation(async () => Response.json({ items: [PUMCH] }));
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("OrganizationPicker · 输入", () => {
  it("只输入 1 个字：提示再输入一个字，不请求", async () => {
    const user = renderPicker();
    expect(screen.getByRole("combobox")).toHaveAttribute(
      "placeholder",
      "搜索医院、高校或研究机构，中英文都可以",
    );
    await typeAndSettle(user, "协");
    expect(screen.getByText("再输入一个字")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("2 个字：防抖后请求，候选主行中文名、副行英文名 · 城市 · 国家 + 命中说明，右侧类型", async () => {
    const user = renderPicker();
    await typeAndSettle(user, "协和");
    expect(fetchMock).toHaveBeenCalledWith("/api/organizations/suggest?q=%E5%8D%8F%E5%92%8C");
    const option = await screen.findByRole("option", { name: /北京协和医院/ });
    expect(option).toHaveTextContent(
      "Peking Union Medical College Hospital · Beijing · 中国 · 经「PUMCH」命中",
    );
    expect(option).toHaveTextContent("医疗");
  });

  it("选中 ROR 机构：变成卡片，带 ROR 小标", async () => {
    const user = renderPicker();
    await typeAndSettle(user, "协和");
    await user.click(await screen.findByRole("option", { name: /北京协和医院/ }));
    expect(onChange).toHaveBeenCalledWith({
      kind: "ror",
      rorId: "04jztag35",
      name: "北京协和医院",
      detail: "Peking Union Medical College Hospital · Beijing · 中国",
    });
    expect(screen.getByText("ROR")).toHaveAttribute("title", "来自 ROR 机构库");
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });

  it("最后一项「直接使用」：选中后是手填机构，卡片副行「手动填写」、没有小标", async () => {
    const user = renderPicker();
    await typeAndSettle(user, "某某医院");
    await user.click(await screen.findByRole("option", { name: /直接使用「某某医院」/ }));
    expect(onChange).toHaveBeenCalledWith({ kind: "free", name: "某某医院" });
    expect(screen.getByText("手动填写")).toBeInTheDocument();
    expect(screen.queryByText("ROR")).not.toBeInTheDocument();
  });

  it("超过 200 字：「直接使用」禁用并说明", async () => {
    fetchMock.mockImplementation(async () => Response.json({ items: [] }));
    const user = renderPicker();
    await user.click(screen.getByRole("combobox"));
    await user.paste("院".repeat(201));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });
    const free = await screen.findByRole("option", { name: /直接使用/ });
    expect(free).toHaveAttribute("aria-disabled", "true");
    expect(free).toHaveTextContent("名称最长 200 字");
  });

  it("没有结果：提示没有找到，仍可直接使用", async () => {
    fetchMock.mockImplementation(async () => Response.json({ items: [] }));
    const user = renderPicker();
    await typeAndSettle(user, "火星医院");
    expect(await screen.findByText("没有找到匹配的机构")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /直接使用「火星医院」/ })).toBeInTheDocument();
  });

  it("接口失败：提示暂时不可用，可以直接填写", async () => {
    fetchMock.mockImplementation(async () => Response.json({ items: [] }, { status: 502 }));
    const user = renderPicker();
    await typeAndSettle(user, "协和");
    expect(await screen.findByText("机构搜索暂时不可用，可以直接填写名称")).toBeInTheDocument();
  });
});

describe("OrganizationPicker · Esc", () => {
  it("第一次 Esc 收起候选、保留输入；收起后再按 Esc 清空输入", async () => {
    const user = renderPicker();
    await typeAndSettle(user, "协和");
    const input = screen.getByRole("combobox");
    expect(input).toHaveAttribute("aria-expanded", "true");
    await user.keyboard("{Escape}");
    expect(input).toHaveAttribute("aria-expanded", "false");
    expect(input).toHaveValue("协和");
    await user.keyboard("{Escape}");
    expect(input).toHaveValue("");
  });
});

describe("OrganizationPicker · 已选", () => {
  it("回显已保存的 ROR 机构（没有副行信息）：副行写「ROR 机构库」", () => {
    renderPicker({ kind: "ror", rorId: "04jztag35", name: "北京协和医院", detail: null });
    expect(screen.getByText("北京协和医院")).toBeInTheDocument();
    expect(screen.getByText("ROR 机构库")).toBeInTheDocument();
  });

  it("更换：回到输入态，预填当前名称", async () => {
    const user = renderPicker({ kind: "free", name: "某某医院" });
    await user.click(screen.getByRole("button", { name: "更换" }));
    expect(screen.getByRole("combobox")).toHaveValue("某某医院");
  });

  it("清除：值变成 null", async () => {
    const user = renderPicker({ kind: "free", name: "某某医院" });
    await user.click(screen.getByRole("button", { name: "清除机构" }));
    expect(onChange).toHaveBeenCalledWith(null);
    expect(screen.getByRole("combobox")).toHaveValue("");
  });
});
