# Providers Quick Reference (PubMed, Europe PMC, Crossref)

Goal: one-page mapping snapshot to implement or debug provider requests quickly.

Boolean query carrier (std_key → provider parameter)
- PubMed: `query → term`
- Europe PMC: `query → query`
- Crossref: `query → query`

Common std_key mappings
- PubMed: `from → mindate`, `to → maxdate` (often with `TO_EXCLUSIVE_MINUS_1D`), `datetype → datetype`, `limit → retmax`, `offset → retstart`
- Europe PMC: `from → fromDate`, `to → toDate`, `limit → pageSize`, `offset → cursorMark|page` (depending on API), filters join into `query` or separate params per endpoint
- Crossref: `from → filter:from-pub-date`, `to → filter:until-pub-date`, `limit → rows`, `offset → offset`, additional filters join with comma in `filter`

Renderer vs Compiler responsibility
- Renderer: emits std_key values and builds boolean query fragments; it does not know provider parameter names.
- Compiler: maps std_keys to provider names (single naming stage) and applies `transform_code`; bridges `std_key=query` into the provider query parameter.

NOT/OR semantics
- PubMed: supports phrase syntax (e.g., "term"[TIAB]); NOT semantics constrained around date range—prefer warning in non-STRICT, error in STRICT when unsupported.
- Europe PMC: OR/NOT supported in query param.
- Crossref: OR/NOT supported; date constraints commonly expressed via `filter`.

Tips
- Use transforms to join MULTI values (e.g., `FILTER_JOIN`, `LIST_JOIN`).
- Prefer exclusive `to` in std_key then apply an inclusive transform per provider when needed.
- Keep label cardinality bounded in metrics: `{provenance, endpoint}`.

For full details see provider deep dives in docs/expr/archive/04/05/06-provider-*.md.
