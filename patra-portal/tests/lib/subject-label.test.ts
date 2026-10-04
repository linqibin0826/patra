import { describe, expect, it } from "vitest";
import { formatSubject } from "@/lib/subject-label";

describe("formatSubject", () => {
  it("全大写学科名转为标题式大小写", () => {
    expect(formatSubject("MEDICINE, GENERAL & INTERNAL")).toBe("Medicine, General & Internal");
    expect(formatSubject("PUBLIC, ENVIRONMENTAL & OCCUPATIONAL HEALTH")).toBe(
      "Public, Environmental & Occupational Health",
    );
  });

  it("介词 / 连词在词中小写，句首仍大写；连字符两侧分别首字母大写", () => {
    expect(formatSubject("HISTORY & PHILOSOPHY OF SCIENCE")).toBe(
      "History & Philosophy of Science",
    );
    expect(formatSubject("OF MICE AND MEN")).toBe("Of Mice and Men");
    expect(formatSubject("NON-SMALL CELL")).toBe("Non-Small Cell");
  });

  it("中英混排只转英文部分；中文与已有大小写的词原样保留", () => {
    expect(formatSubject("ONCOLOGY 肿瘤学")).toBe("Oncology 肿瘤学");
    expect(formatSubject("医学")).toBe("医学");
    expect(formatSubject("Cell Biology")).toBe("Cell Biology");
  });
});
