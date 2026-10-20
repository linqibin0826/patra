import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MathMl } from "@/components/portal/fulltext/MathMl";

describe("MathMl", () => {
  it("按白名单渲染成真实的 MathML 元素（浏览器原生排版）", () => {
    const { container } = render(
      <MathMl mathml='<math display="block"><msub><mi>N</mi><mn>1</mn></msub></math>' />,
    );
    const math = container.querySelector("math");
    expect(math?.namespaceURI).toBe("http://www.w3.org/1998/Math/MathML");
    expect(math?.getAttribute("display")).toBe("block");
    expect(container.querySelector("msub mi")?.textContent).toBe("N");
  });

  it("mtd 的 rowspan 属性照常输出", () => {
    const { container } = render(
      <MathMl mathml='<math><mtable><mtr><mtd rowspan="2"><mi>a</mi></mtd></mtr></mtable></math>' />,
    );
    expect(container.querySelector("mtd")?.getAttribute("rowspan")).toBe("2");
  });

  it("去掉的属性不会出现在页面上", () => {
    const { container } = render(
      <MathMl mathml='<math><mi onclick="x()" style="a">a</mi></math>' />,
    );
    const mi = container.querySelector("mi");
    expect(mi?.hasAttribute("onclick")).toBe(false);
    expect(mi?.hasAttribute("style")).toBe(false);
  });
});
