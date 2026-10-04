import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Venation } from "@/components/portal/Venation";

describe("Venation", () => {
  it("同页多片叶脉各用各的描边渐变：调用方不传 id，组件自己生成不重复的 id", () => {
    const { container } = render(
      <>
        <Venation />
        <Venation />
      </>,
    );
    const gradients = [...container.querySelectorAll("linearGradient")].map((g) => g.id);
    expect(new Set(gradients).size).toBe(2);
    const strokes = [...container.querySelectorAll("svg > g")].map((g) => g.getAttribute("stroke"));
    expect(strokes).toEqual(gradients.map((id) => `url(#${id})`));
  });
});
