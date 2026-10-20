import { render, screen } from "@testing-library/react";
import { makePaperDetail } from "@tests/fixtures/paper";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PaperRail } from "@/components/portal/paper-detail/PaperRail";

describe("PaperRail", () => {
  it("只有标识卡与 AI 速读卡；不再有操作卡、速览卡（PAP-89）", () => {
    render(<PaperRail paper={makePaperDetail({ doi: "10.1/x", hasFullText: true })} />);
    expect(screen.getByRole("region", { name: "标识" })).toBeInTheDocument();
    expect(
      screen.getByText("AI 速读即将上线，上线后也只作线索，不能替代阅读原文。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /生成速读/ })).toBeDisabled();
    expect(screen.queryByText("操作")).toBeNull();
    expect(screen.queryByText("速览")).toBeNull();
    expect(screen.queryByRole("link", { name: /阅读全文/ })).toBeNull();
  });
});
