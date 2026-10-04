import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { FacetCheckRow } from "@/components/portal/browse/FacetCheckRow";
import { FacetGroup } from "@/components/portal/browse/FacetGroup";
import { FacetToggleRow } from "@/components/portal/browse/FacetToggleRow";

describe("FacetGroup", () => {
  it("默认展开，fieldset 以组名为可访问名；点击标题折叠", () => {
    render(
      <FacetGroup title="文献类型" selCount={0}>
        <span>内容</span>
      </FacetGroup>,
    );
    const toggle = screen.getByRole("button", { name: /文献类型/ });
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("group", { name: "文献类型" })).toBeInTheDocument();
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("内容")).not.toBeInTheDocument();
  });

  it("有已选项时显示角标", () => {
    render(
      <FacetGroup title="证据等级" selCount={2}>
        <span />
      </FacetGroup>,
    );
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("标题按钮的可访问名写出已选数（不只念一个孤零零的数字）", () => {
    const { rerender } = render(
      <FacetGroup title="JCR 分区" selCount={1}>
        <span />
      </FacetGroup>,
    );
    expect(screen.getByRole("button", { name: "JCR 分区，已选 1 项" })).toBeInTheDocument();
    rerender(
      <FacetGroup title="JCR 分区" selCount={0}>
        <span />
      </FacetGroup>,
    );
    expect(screen.getByRole("button", { name: "JCR 分区" })).toBeInTheDocument();
  });
});

describe("FacetCheckRow", () => {
  it("checkbox：千分位计数，可访问名为标签本身（不含计数），点击触发 onToggle", () => {
    const onToggle = vi.fn();
    render(
      <FacetCheckRow label="综述 · Review" count={2040} checked={false} onToggle={onToggle} />,
    );
    expect(screen.getByText("2,040")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: "综述 · Review" }));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it("计数为 0 且未选中时变淡", () => {
    render(<FacetCheckRow label="化学" count={0} checked={false} onToggle={() => {}} />);
    expect(screen.getByText("化学").closest("label")?.className).toMatch(/is-zero|opacity/);
  });

  it("radio：点击已选中项也触发 onToggle（再点取消）", () => {
    const onToggle = vi.fn();
    render(
      <FacetCheckRow type="radio" name="year" label="2026" count={5} checked onToggle={onToggle} />,
    );
    fireEvent.click(screen.getByRole("radio", { name: "2026" }));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it("checkbox 的勾线是可描出的装饰路径：勾选时描线，只在 motion-safe 下带过渡", () => {
    const { container } = render(<FacetCheckRow label="综述" checked onToggle={() => {}} />);
    const checkbox = screen.getByRole("checkbox", { name: "综述" });
    const mark = container.querySelector("svg[data-check-mark]");
    expect(mark).toHaveAttribute("aria-hidden", "true");
    expect(mark?.querySelector("path")).toHaveAttribute("pathLength", "1");
    // 勾线紧跟 input，靠 peer-checked 切换描线进度
    expect(checkbox.className).toMatch(/\bpeer\b/);
    expect(mark?.getAttribute("class")).toMatch(/peer-checked:\[stroke-dashoffset:0\]/);
    expect(mark?.getAttribute("class")).toMatch(/motion-safe:transition-\[stroke-dashoffset\]/);
  });

  it("未传 count 时不渲染计数；muted 时弱化文字", () => {
    render(<FacetCheckRow label="未分级" checked={false} onToggle={() => {}} muted />);
    const label = screen.getByText("未分级").closest("label");
    expect(label?.className).toMatch(/fg-3/);
    expect(label?.textContent).toBe("未分级");
  });
});

describe("FacetToggleRow", () => {
  it("渲染开关与计数，点击触发 onToggle", () => {
    const onToggle = vi.fn();
    render(<FacetToggleRow label="仅开放获取" count={1234} checked={false} onToggle={onToggle} />);
    expect(screen.getByText("1,234")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: "仅开放获取" }));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });
});
