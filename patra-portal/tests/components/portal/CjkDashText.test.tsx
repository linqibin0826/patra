import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { CjkDashText } from "@/components/portal/CjkDashText";

describe("CjkDashText", () => {
  it("破折号交给中文字体渲染（拉丁字体里两段不相连），其余文字原样", () => {
    const { container } = render(
      <p>
        <CjkDashText>叶脉——十个来源，汇入同一根主脉——即统一索引。</CjkDashText>
      </p>,
    );
    expect(container).toHaveTextContent("叶脉——十个来源，汇入同一根主脉——即统一索引。");
    const dashes = container.querySelectorAll("[data-cjk-dash]");
    expect(dashes).toHaveLength(2);
    for (const d of dashes) {
      expect(d).toHaveTextContent(/^——$/);
      // 字距清零：tracking 会在两段之间再撑出缝
      expect(d).toHaveClass("font-[family-name:var(--patra-font-cjk-sans)]", "tracking-normal");
    }
  });

  it("没有破折号时只输出原文", () => {
    const { container } = render(
      <p>
        <CjkDashText>没有破折号</CjkDashText>
      </p>,
    );
    expect(container.querySelector("p")?.innerHTML).toBe("没有破折号");
  });
});
