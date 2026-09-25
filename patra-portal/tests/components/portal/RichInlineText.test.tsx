import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RichInlineText } from "@/components/portal/RichInlineText";

describe("RichInlineText", () => {
  it("渲染出真实的 sub 元素", () => {
    const { container } = render(<RichInlineText text="CO<sub>2</sub> fixation" />);
    // sub/sup 无 landmark role 且 aria-query 的 subscript 映射不稳定，querySelector 是此处最后手段
    const sub = container.querySelector("sub");
    expect(sub).not.toBeNull();
    expect(sub?.textContent).toBe("2");
    expect(container.textContent).toBe("CO2 fixation");
  });

  it("恶意输入不产生可执行元素，文本字面保留", () => {
    const { container } = render(
      <RichInlineText text={'<img src=x onerror=alert(1)><i onclick="x">ok</i>'} />,
    );
    expect(container.querySelector("img")).toBeNull();
    const i = container.querySelector("i");
    expect(i?.getAttribute("onclick")).toBeNull();
    expect(i?.textContent).toBe("ok");
    expect(container.textContent).toContain("<img src=x onerror=alert(1)>");
  });

  it("MathML 结构渲染为对应元素树", () => {
    const { container } = render(
      <RichInlineText text="<math><msub><mi>T</mi><mn>2</mn></msub></math>" />,
    );
    const MATHML_NS = "http://www.w3.org/1998/Math/MathML";
    expect(container.querySelector("math")?.namespaceURI).toBe(MATHML_NS);
    expect(container.querySelector("math msub mi")?.textContent).toBe("T");
  });

  it("空字符串渲染为空且不崩溃", () => {
    const { container } = render(<RichInlineText text="" />);
    expect(container.innerHTML).toBe("");
  });

  it("自闭合空元素正常渲染（空 children 数组）", () => {
    const { container } = render(<RichInlineText text="<math><mspace/></math>" />);
    expect(container.querySelector("math mspace")).not.toBeNull();
  });

  it("highlight 命中片段渲染为 mark", () => {
    const { container } = render(<RichInlineText text="Semaglutide and GLP-1" highlight="glp-1" />);
    // mark 无稳定 ARIA role，querySelector 是此处最后手段
    const marks = container.querySelectorAll("mark");
    expect(marks).toHaveLength(1);
    expect(marks[0]?.textContent).toBe("GLP-1");
  });

  it("不传 highlight 时不产生 mark", () => {
    const { container } = render(<RichInlineText text="GLP-1" />);
    expect(container.querySelector("mark")).toBeNull();
  });
});
