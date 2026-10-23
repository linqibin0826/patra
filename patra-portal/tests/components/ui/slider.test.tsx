import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Slider } from "@/components/ui/slider";

function Harness() {
  const [value, setValue] = useState(1);
  return (
    <>
      <Slider aria-label="缩放" value={value} min={1} max={3} step={0.1} onValueChange={setValue} />
      <output>{value.toFixed(1)}</output>
    </>
  );
}

describe("Slider", () => {
  it("role=slider，方向键改值", () => {
    render(<Harness />);
    const slider = screen.getByRole("slider", { name: "缩放" });
    fireEvent.keyDown(slider, { key: "ArrowRight" });
    expect(screen.getByRole("status")).toHaveTextContent("1.1");
  });
});
