/// 导出格式（简报 §3.6）：导出对话框的格式卡与分享面板的「导出这篇」共用
export const EXPORT_FORMATS = [
  { id: "ris", name: "RIS", ext: "ris", hint: "Zotero、EndNote、NoteExpress、Mendeley" },
  { id: "bibtex", name: "BibTeX", ext: "bib", hint: "LaTeX、Overleaf" },
  { id: "csv", name: "CSV", ext: "csv", hint: "Excel 等表格软件" },
  { id: "csl-json", name: "CSL-JSON", ext: "json", hint: "Zotero、Pandoc 等支持 CSL 的工具" },
] as const;
export type ExportFormat = (typeof EXPORT_FORMATS)[number]["id"];
