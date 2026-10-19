# JATS 解析夹具

PAP-86 解析器的单元测试与夹具全量测试（`JatsBodyParserAdapterTest`、`JatsFixtureSweepTest`）使用。

- 真实文章均取自 PMC Cloud Service 公开桶 `pmc-oa-opendata`（2026-10-19 下载），全部是 **CC BY 4.0** 许可，按许可要求注明来源：文件名即 PMCID，出处为 PubMed Central（https://pmc.ncbi.nlm.nih.gov/articles/<PMCID>/）。
- 文件内容只去掉了行尾空白（仓库的提交钩子会强制去掉），其余与原文一致。
- `no-body-synthetic.xml` 是手写的合成夹具（非真实文章），覆盖"没有 `<body>`"的情况。

| 文件 | PMCID | 出版商 · 期刊 | 类型 | 许可 | 覆盖的特征 |
|---|---|---|---|---|---|
| `PMC12569377.xml` | PMC12569377 | Cell Press (Elsevier) · Cell Reports | research-article | CC BY 4.0 | 多级章节、图、表、合并单元格、列表、element-citation |
| `PMC12670376.xml` | PMC12670376 | Wiley · Statistics in Medicine | research-article | CC BY 4.0 | 多级章节、MathML、图、表、合并单元格、列表、boxed-text、mixed-citation |
| `PMC12673409.xml` | PMC12673409 | American Medical Association · JAMA Network Open | research-article | CC BY 4.0 | 图、mixed-citation |
| `PMC12747861.xml` | PMC12747861 | SAGE · Hand (New York, N.Y.) | research-article | CC BY 4.0 | 多级章节、图、表、合并单元格、mixed-citation |
| `PMC12754789.xml` | PMC12754789 | Oxford University Press · NAR Genomics and Bioinformatics | research-article | CC BY 4.0 | 多级章节、只有 TeX、图、表、列表、mixed-citation |
| `PMC12755782.xml` | PMC12755782 | PLOS · PLOS One | correction | CC BY 4.0 | mixed-citation |
| `PMC12755881.xml` | PMC12755881 | eLife · eLife | research-article | CC BY 4.0 | 多级章节、MathML、图、列表、element-citation、sub-article、disp-quote |
| `PMC12756210.xml` | PMC12756210 | Springer · Stress Biology | research-article | CC BY 4.0 | 多级章节、图、mixed-citation、element-citation、citation-alternatives |
| `PMC12767392.xml` | PMC12767392 | Wiley (Hindawi) · Mediators of Inflammation | research-article | CC BY 4.0 | 多级章节、图、mixed-citation |
| `PMC12768384.xml` | PMC12768384 | PLOS · PLOS Computational Biology | research-article | CC BY 4.0 | 多级章节、MathML、图、列表、mixed-citation、sub-article |
| `PMC12777783.xml` | PMC12777783 | Taylor & Francis · Renal Failure | research-article | CC BY 4.0 | 多级章节、图、表、mixed-citation |
| `PMC12779161.xml` | PMC12779161 | PLOS · PLOS Computational Biology | research-article | CC BY 4.0 | 多级章节、MathML、图、mixed-citation |
| `PMC12781401.xml` | PMC12781401 | BioMed Central · Journal of Neuroinflammation | research-article | CC BY 4.0 | 多级章节、只有 TeX、图、mixed-citation、element-citation、citation-alternatives |
| `PMC12800745.xml` | PMC12800745 | Elsevier · The Lancet Regional Health: Western Pacific | editorial | CC BY 4.0 |  |
| `PMC12838083.xml` | PMC12838083 | MDPI · Antibiotics | research-article | CC BY 4.0 | 多级章节、图、表、合并单元格、element-citation |
| `PMC12848012.xml` | PMC12848012 | Nature Portfolio · Communications Biology | research-article | CC BY 4.0 | 多级章节、图、表、合并单元格、mixed-citation、element-citation、citation-alternatives |
| `PMC12864740.xml` | PMC12864740 | Springer Nature · Nature Communications | research-article | CC BY 4.0 | 多级章节、MathML、图、mixed-citation、element-citation、citation-alternatives |
| `PMC13335072.xml` | PMC13335072 | Frontiers · Frontiers in Public Health | research-article | CC BY 4.0 | 多级章节、MathML、图、表、mixed-citation |
| `PMC13583816.xml` | PMC13583816 | BMJ · BMJ Open | research-article | CC BY 4.0 | 多级章节、图、表、合并单元格、列表、boxed-text、element-citation、sub-article |
| `PMC13625049.xml` | PMC13625049 | Frontiers · Frontiers in Endocrinology | systematic-review | CC BY 4.0 | 多级章节、图、表、合并单元格、列表、mixed-citation |
| `no-body-synthetic.xml` | — | 合成 | research-article | — | 无 body、element-citation |
