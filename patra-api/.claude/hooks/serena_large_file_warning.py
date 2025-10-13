#!/usr/bin/env python3
"""
Large File Read Warning Hook
Warns when reading large files without first using Serena's get_symbols_overview.
Encourages token-efficient exploration through symbolic navigation.
"""
import json
import sys
import os
import subprocess

try:
    data = json.load(sys.stdin)
    file_path = data.get('tool_input', {}).get('file_path', '')

    # Only check Java source files
    if not file_path or not file_path.endswith('.java') or '/test/' in file_path:
        sys.exit(0)

    # Check file size
    if os.path.exists(file_path):
        try:
            result = subprocess.run(['wc', '-l', file_path],
                                  capture_output=True, text=True, timeout=2)
            line_count = int(result.stdout.split()[0])
        except (subprocess.TimeoutExpired, ValueError, IndexError):
            sys.exit(0)

        # Warning for files > 200 lines
        if line_count > 200:
            # Create temp directory if it doesn't exist
            temp_dir = "/tmp/serena_state"
            os.makedirs(temp_dir, exist_ok=True)

            state_file = f"{temp_dir}/explored_{abs(hash(file_path))}.marker"

            if not os.path.exists(state_file):
                print(f"⚠️  SERENA SUGGESTION: Large file ({line_count} lines)")
                print(f"📖 Consider using Serena first for token efficiency:")
                print(f"")
                print(f"   1. mcp__serena__get_symbols_overview")
                print(f"      → Get file overview with symbol list")
                print(f"")
                print(f"   2. mcp__serena__find_symbol")
                print(f"      → Read specific symbols only")
                print(f"")
                print(f"   3. Read full file only if necessary")
                print(f"")
                print(f"💡 This saves tokens and focuses on relevant code")
                print(f"")

                # Create marker to show we warned once
                open(state_file, 'w').write('1')

    sys.exit(0)  # Warning only, don't block

except Exception as e:
    # Don't fail the workflow on hook errors
    print(f"Hook error (non-fatal): {e}", file=sys.stderr)
    sys.exit(0)
