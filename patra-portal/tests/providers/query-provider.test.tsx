import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { useQueryClient } from "@tanstack/react-query";
import { QueryProvider } from "@/providers/query-provider";

function ProbeChild() {
  const client = useQueryClient();
  return <span data-testid="probe">{client ? "has-client" : "no-client"}</span>;
}

describe("QueryProvider", () => {
  it("provides a QueryClient to children", () => {
    render(
      <QueryProvider>
        <ProbeChild />
      </QueryProvider>,
    );
    expect(screen.getByTestId("probe")).toHaveTextContent("has-client");
  });
});
