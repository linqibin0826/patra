import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

describe("Card", () => {
  it("组合渲染 Header/Title/Description/Content", () => {
    render(
      <Card>
        <CardHeader>
          <CardTitle>测试标题</CardTitle>
          <CardDescription>测试描述</CardDescription>
        </CardHeader>
        <CardContent>测试内容</CardContent>
      </Card>,
    );
    expect(screen.getByText("测试标题")).toBeInTheDocument();
    expect(screen.getByText("测试描述")).toBeInTheDocument();
    expect(screen.getByText("测试内容")).toBeInTheDocument();
  });

  it("透传 className 到根 Card", () => {
    const { container } = render(<Card className="custom-card">x</Card>);
    expect(container.firstChild).toHaveClass("custom-card");
  });

  it("size=sm 透传 data-size 属性", () => {
    const { container } = render(<Card size="sm">x</Card>);
    expect(container.firstChild).toHaveAttribute("data-size", "sm");
  });
});
