Use them proactively!

### sequential-thinking
- **Purpose**: Deep analysis and step-by-step problem solving
- **Use when**: Complex multi-step tasks, architectural decisions, or debugging intricate issues
- **Benefits**: Structured reasoning process, helps break down complex problems systematically
- **How**: Call the `mcp__sequential-thinking__sequentialthinking` tool

### context7
- **Purpose**: Fetch up-to-date documentation for libraries and frameworks
- **Use when**: Need current API references, version-specific documentation, or best practices
- **Benefits**: Always current information beyond model knowledge cutoff, verified technical details
- **How**: Use `mcp__context7__resolve-library-id` then `mcp__context7__get-library-docs`

### serena
- **Purpose**: Semantic code navigation and intelligent editing
- **Use when**: Understanding codebase structure, finding symbols, analyzing dependencies, or making precise code modifications
- **Benefits**: Token-efficient code exploration, symbol-based editing, avoid reading entire files unnecessarily
- **Key capabilities**: Overview files, find symbols by name path, search patterns, trace references, edit by symbol
- **How**: Use tools like:
    - `mcp__serena__get_symbols_overview`: Get file overview
    - `mcp__serena__find_symbol`: Find symbols by name path
    - `mcp__serena__find_referencing_symbols`: Find references
    - `mcp__serena__replace_symbol_body`: Replace symbol implementation

**IMPORTANT**: Use serena tools to avoid reading entire files. Start with `get_symbols_overview`, then use `find_symbol` for targeted reads.
