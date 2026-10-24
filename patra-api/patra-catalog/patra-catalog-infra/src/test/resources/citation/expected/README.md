# 引用期望文本

每行 `label<TAB>text`，APA 没有 label。三篇样例见 `CitationRecordFixture`（spec 第 7.4 节）。期望文本由 citeproc-java 3.5.2 + `org.citationstyles:styles/locales` 26.8、`en-US` 生成（2026-10-24），再逐条对照各样式指南人工核对。夹具证明「不回退」，不证明权威正确。

- `gb-t-7714-2015.txt`：对照 GB/T 7714-2015 第 8 节与附录 A：著者姓全大写、名缩写不加点；超过 3 位著者写前 3 位加「et al.」（§8.1.2 西文用 et al.）；有 DOI 的标 `[J/OL]`；「年, 卷(期): 页码.」后接 `DOI:`；只到年、只到年月的条目都不出现月日。核对通过。
- `vancouver.txt`：对照 NLM《Citing Medicine》第 1 章：作者 `Anker SD` 形式，超过 6 位写前 6 位加「et al.」；期刊用 NLM 缩写；`2021 Oct 14;385(16):1451–61` 页码缩写；只到年月为 `2022 Oct`；末尾 PMID / PMCID 是样式自带。核对通过。
- `apa-7.txt`：对照 APA 7th §9.8、§10.1：最多 20 位作者全列、末位前加 `&`；`(年).`；期刊名与卷号斜体（纯文本里不体现）；无页码用 `Article 12345`；DOI 写成 https 链接；按作者字母排序。核对通过。
- `ama-11.txt`：对照 AMA Manual of Style 11th §3.11、§3.15：超过 6 位作者写前 3 位加「et al.」；`2021;385(16):1451-1461` 全页码；`doi:` 小写。核对通过。
