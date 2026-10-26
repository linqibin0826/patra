import { describe, expect, it } from "vitest";
import { filenameFromDisposition } from "@/lib/download";

describe("filenameFromDisposition", () => {
  it("优先取 RFC 5987 的 filename*", () => {
    expect(
      filenameFromDisposition(
        "attachment; filename=\"patra-export.ris\"; filename*=UTF-8''patra-%E7%BB%84%E4%BC%9A-2026-10-24.ris",
      ),
    ).toBe("patra-组会-2026-10-24.ris");
  });

  it("没有 filename* 时取 filename", () => {
    expect(filenameFromDisposition('attachment; filename="patra-library-2026-10-24.bib"')).toBe(
      "patra-library-2026-10-24.bib",
    );
  });

  it("都没有：patra-export", () => {
    expect(filenameFromDisposition(null)).toBe("patra-export");
  });
});
