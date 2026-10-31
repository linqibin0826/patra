import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import Loading from "@/app/settings/notifications/loading";

describe("设置 › 通知的骨架", () => {
  it("读屏只读到一句「正在读取通知设置」，没有单选", () => {
    render(<Loading />);
    expect(screen.getByRole("status")).toHaveTextContent("正在读取通知设置");
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
  });
});
