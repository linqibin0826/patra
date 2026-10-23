import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { CharCounter } from "@/components/settings/profile/CharCounter";

describe("CharCounter", () => {
  it.each([
    ["字".repeat(10), "10 / 30", "text-fg-3"],
    ["字".repeat(24), "24 / 30", "text-amber-700"],
    ["字".repeat(31), "31 / 30", "text-status-danger"],
    ["👨‍👩‍👧", "1 / 30", "text-fg-3"],
    ["👨‍👩‍👧".repeat(7), "7 / 30", "text-status-danger"],
  ])("%j → %s（%s）", (value, text, tone) => {
    render(<CharCounter value={value} max={30} />);
    expect(screen.getByText(text)).toHaveClass(tone);
  });
});
