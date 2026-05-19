import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import HomePage from "@/app/page";

describe("HomePage smoke", () => {
  it("renders the patra-portal landing heading", () => {
    render(<HomePage />);
    expect(screen.getByRole("heading", { level: 1, name: /patra-portal/i })).toBeInTheDocument();
  });
});
