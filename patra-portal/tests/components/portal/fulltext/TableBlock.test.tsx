import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { TableBlock, type TableBlockRow } from "@/components/portal/fulltext/TableBlock";

const cell = (content: string, overrides: Partial<TableBlockRow[number]> = {}) => ({
  content,
  colspan: 1,
  rowspan: 1,
  header: false,
  ...overrides,
});

function bodyRows(n: number): TableBlockRow[] {
  return Array.from({ length: n }, (_, i) => [cell(`Row ${i + 1}`), cell(`${i}`)]);
}

function renderTable(rows: TableBlockRow[], footnotes: string | null = null) {
  return render(
    <TableBlock
      anchorId="ft-t2"
      label="Table 2"
      title="Baseline characteristics"
      head={[[cell("Variable", { header: true }), cell("Value", { header: true })]]}
      body={rows}
      footnotes={footnotes ? <p>{footnotes}</p> : null}
    />,
  );
}

describe("TableBlock", () => {
  it("表题在表上方作为表格的 caption；表注在表下方", () => {
    renderTable(bodyRows(3), "SD, standard deviation.");
    const table = screen.getByRole("table", { name: /Table 2.*Baseline characteristics/ });
    expect(table.closest("[id]")).toHaveAttribute("id", "ft-t2");
    expect(screen.getByText("SD, standard deviation.")).toBeInTheDocument();
  });

  it("表头是列标题；表体里的表头单元格是行标题；合并单元格照原表", () => {
    render(
      <TableBlock
        anchorId={undefined}
        label={null}
        title="T"
        head={[[cell("Group", { header: true, colspan: 2 })]]}
        body={[[cell("Age", { header: true, rowspan: 2 }), cell("40")]]}
        footnotes={null}
      />,
    );
    const colHeader = screen.getByRole("columnheader", { name: "Group" });
    expect(colHeader).toHaveAttribute("colspan", "2");
    const rowHeader = screen.getByRole("rowheader", { name: "Age" });
    expect(rowHeader).toHaveAttribute("rowspan", "2");
  });

  it("不超过 12 行全显示，没有展开按钮", () => {
    renderTable(bodyRows(12));
    expect(screen.getAllByRole("row")).toHaveLength(13);
    expect(screen.queryByRole("button")).toBeNull();
  });

  it("超过 12 行只显示前 10 行，「展开全部 N 行」后全部显示，按钮变「收起」", async () => {
    const user = userEvent.setup();
    renderTable(bodyRows(66));
    expect(screen.getAllByRole("row")).toHaveLength(11);

    await user.click(screen.getByRole("button", { name: "展开全部 66 行" }));
    expect(screen.getAllByRole("row")).toHaveLength(67);

    await user.click(screen.getByRole("button", { name: "收起" }));
    expect(screen.getAllByRole("row")).toHaveLength(11);
    expect(screen.queryByRole("cell", { name: "Row 11" })).toBeNull();
  });

  it("表题里的引用链接对读屏和键盘都可用，表格以可见表题为名称", () => {
    render(
      <TableBlock
        anchorId="ft-t3"
        label="Table 3"
        title={
          <>
            Outcomes <a href="#ref-r12">12</a>
          </>
        }
        head={[[cell("Outcome", { header: true })]]}
        body={[[cell("Death")]]}
        footnotes={null}
      />,
    );
    expect(screen.getByRole("table", { name: /Table 3.*Outcomes 12/ })).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "12" })).toHaveLength(1);
  });
});

describe("TableBlock · 三线表（PAP-89）", () => {
  function renderNumeric() {
    return render(
      <TableBlock
        anchorId="ft-t10"
        label="Table 10"
        title="Comparison with others"
        head={[[cell("Study", { header: true }), cell("Accuracy", { header: true })]]}
        body={[
          [cell("Tsai et al."), cell("91%")],
          [cell("Nopour (2024)"), cell("93.43%")],
        ]}
        numeric={[false, true]}
        footnotes={null}
      />,
    );
  }

  it("数字列的列标题与单元格都带 data-numeric，文字列不带", () => {
    renderNumeric();
    expect(screen.getByRole("columnheader", { name: "Accuracy" })).toHaveAttribute("data-numeric");
    expect(screen.getByRole("cell", { name: "91%" })).toHaveAttribute("data-numeric");
    expect(screen.getByRole("columnheader", { name: "Study" })).not.toHaveAttribute("data-numeric");
  });

  it("表题挂在标签栏：标签格放原文标签，表题不加粗", () => {
    renderNumeric();
    const label = screen.getByText("Table 10");
    expect(label.closest(".grid")?.firstElementChild).toContainElement(label);
    expect(screen.getByText("Comparison with others").className).not.toContain("font-medium");
  });

  it("表格外层不再有圆角边框盒", () => {
    renderNumeric();
    const scroller = screen.getByRole("table").parentElement?.parentElement as HTMLElement;
    expect(scroller.className).not.toContain("rounded-lg");
    expect(scroller.className).not.toContain("border-border-default");
  });

  it("跨行单元格之后的行，数字对齐落在正确的列上", () => {
    render(
      <TableBlock
        anchorId="ft-t3"
        label="Table 3"
        title="Grouped"
        head={[]}
        body={[
          [cell("Group", { rowspan: 2 }), cell("A"), cell("12")],
          [cell("B"), cell("56")],
        ]}
        numeric={[false, false, true]}
        footnotes={null}
      />,
    );
    expect(screen.getByRole("cell", { name: "56" })).toHaveAttribute("data-numeric");
    expect(screen.getByRole("cell", { name: "B" })).not.toHaveAttribute("data-numeric");
  });
});
