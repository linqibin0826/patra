import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { FormAlert } from "@/components/auth/FormAlert";

describe("FormAlert", () => {
  it("是 alert，读屏插入时就读出；带图标，不只靠颜色", () => {
    render(<FormAlert>邮箱或密码错误</FormAlert>);
    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("邮箱或密码错误");
    expect(alert.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });
});
