---
allowed-tools: Bash(mvn:*), Bash(grep:*)
description: Quick compile check (no tests) - verify code compiles cleanly
---

# Compile Check (No Tests)

## Context

You are performing a **quick compile check** without running tests.

This is useful for:
- ✅ Fast syntax validation (< 1 minute)
- ✅ Type checking and imports
- ✅ Dependency resolution
- ✅ Before running slow test suite

### Current Git Status

**Modified files**:
!`git diff --name-only HEAD | grep "\.java$" | head -20`

## Your Task

Verify code compiles cleanly across entire project.

### Step 1: Clean Build

Remove previous artifacts:

```bash
mvn clean
```

**Duration**: 5-10 seconds

### Step 2: Compile Source Code

Compile main source code:

```bash
mvn compile -DskipTests
```

**What this checks**:
- ✅ Java syntax valid
- ✅ All imports resolved
- ✅ Type checking passes
- ✅ Dependencies available
- ✅ Annotation processing works

**Duration**: 30-60 seconds

**Expected**: `BUILD SUCCESS`

### Step 3: Compile Test Code

Compile test code (but don't run):

```bash
mvn test-compile -DskipTests
```

**What this checks**:
- ✅ Test syntax valid
- ✅ Test dependencies resolved (JUnit, Mockito, AssertJ)
- ✅ Tests reference source code correctly

**Duration**: 20-40 seconds

**Expected**: `BUILD SUCCESS`

### Step 4: Analyze Compilation Errors (If Any)

If compilation fails, categorize errors:

#### A. Syntax Errors

```
[ERROR] /path/to/File.java:[line,column] ';' expected
[ERROR] /path/to/File.java:[line,column] ')' expected
```

**Fix**: Correct Java syntax

#### B. Type Errors

```
[ERROR] /path/to/File.java:[line,column] incompatible types
  required: String
  found: Integer
```

**Fix**: Correct type mismatch

#### C. Missing Imports

```
[ERROR] /path/to/File.java:[line,column] cannot find symbol
  symbol: class SomeClass
```

**Fix**: Add missing import or dependency

#### D. Missing Dependencies

```
[ERROR] Cannot resolve dependency: com.example:artifact:1.0.0
```

**Fix**: Add to pom.xml or check Maven repository

#### E. Annotation Processing Errors

```
[ERROR] Annotation processor 'lombok.AnnotationProcessor' failed
```

**Fix**: Check Lombok version, annotation usage

### Step 5: Compiler Warnings

Even if compilation succeeds, check for warnings:

```bash
mvn compile 2>&1 | grep "warning"
```

**Common warnings**:

1. **Unused imports**:
   ```
   warning: unused import SomeClass
   ```
   **Action**: Remove unused import

2. **Deprecated API usage**:
   ```
   warning: SomeMethod() is deprecated
   ```
   **Action**: Update to new API

3. **Unchecked conversions**:
   ```
   warning: unchecked conversion
   ```
   **Action**: Add generic types

4. **Missing @Override**:
   ```
   warning: missing @Override annotation
   ```
   **Action**: Add @Override

### Step 6: Per-Module Compilation

If compilation fails, isolate failing module:

```bash
mvn compile -pl :patra-{module} -DskipTests
```

**Modules to check**:
- `patra-common`
- `patra-registry-domain`
- `patra-registry-app`
- `patra-registry-infra`
- `patra-ingest-domain`
- `patra-ingest-app`

**Identify** which module has compilation errors.

## Output Format

```
# Compilation Check Report

**Date**: {current-date}
**Duration**: {X seconds}

## Compilation Results

### Source Code Compilation
- **Status**: ✅ SUCCESS / ❌ FAILURE
- **Duration**: {X}s
- **Classes compiled**: {count}
- **Warnings**: {count}

### Test Code Compilation
- **Status**: ✅ SUCCESS / ❌ FAILURE
- **Duration**: {X}s
- **Test classes compiled**: {count}
- **Warnings**: {count}

## Compilation Errors (if any)

### Module: patra-{module}

**File**: `/path/to/File.java`
**Line**: {line}
**Error**:
```
{error message}
```

**Root Cause**: {analysis}

**Recommendation**: {fix suggestion}

## Compilation Warnings

| File | Line | Warning | Severity |
|------|------|---------|----------|
| Foo.java | 23 | Unused import | Low |
| Bar.java | 45 | Deprecated API | Medium |
| Baz.java | 67 | Unchecked conversion | Medium |

## Overall Status

{If SUCCESS}:
- ✅ All code compiles cleanly
- ✅ {X} warnings (non-blocking)
- ✅ Ready to run tests: `/test-run-all`

{If FAILURE}:
- ❌ Compilation failed in {module}
- ❌ Fix {count} errors before running tests
- ❌ See error details above

## Next Steps

{If SUCCESS with no warnings}:
- Perfect! Run tests: `/test-run-all`
- Or run quick unit tests: `/test-run-unit`

{If SUCCESS with warnings}:
- Optional: Fix warnings for cleaner code
- Run tests: `/test-run-all`

{If FAILURE}:
- Fix compilation errors
- Run `/test-compile-check` again
- Iterate until clean compilation
```

## Use Cases

### 1. Quick Syntax Check

Before running slow tests:
```bash
/test-compile-check  # 30 seconds
# If passes:
/test-run-all        # 5 minutes
```

**Time saved**: 5 minutes if compilation fails

### 2. After Major Refactoring

After renaming classes or methods:
```bash
/test-compile-check  # Verify all references updated
```

### 3. Dependency Updates

After updating pom.xml:
```bash
/test-compile-check  # Verify dependencies resolve
```

### 4. IDE vs Maven Verification

IDE says "no errors", but want to verify Maven agrees:
```bash
/test-compile-check  # Use Maven compiler, not IDE compiler
```

## Performance

**Typical durations**:

| Project Size | Source Compile | Test Compile | Total |
|--------------|----------------|--------------|-------|
| Small (< 100 classes) | 10s | 5s | 15s |
| Medium (100-500 classes) | 30s | 15s | 45s |
| Large (500+ classes) | 60s | 30s | 90s |

**Compare to full test run**: 5-10 minutes

**Speed up**: 5-10x faster than running tests

## Compiler Configuration

**Java version** (from patra-parent/pom.xml):
```xml
<maven.compiler.source>21</maven.compiler.source>
<maven.compiler.target>21</maven.compiler.target>
```

**Encoding**:
```xml
<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
```

**Compiler plugin version**: 3.12.1

## Troubleshooting

### Issue: "Compilation succeeds but tests fail"

**Cause**: Runtime issue, not compile-time

**Examples**:
- NullPointerException
- Missing resources
- Configuration issues

**Solution**: Use `/test-run-all` to find runtime issues

### Issue: "IDE shows error, but Maven compiles fine"

**Cause**: IDE and Maven out of sync

**Solution**:
1. Reimport Maven project in IDE
2. Invalidate IDE caches
3. Trust Maven as source of truth

### Issue: "Annotation processor fails"

**Cause**: Lombok, MapStruct, or other processor issue

**Check**:
```bash
mvn compile -X  # Debug output
```

**Common fixes**:
- Update annotation processor version
- Check annotation usage
- Clean `.m2` cache

## Begin Execution

Now run compile check and provide detailed compilation report.
