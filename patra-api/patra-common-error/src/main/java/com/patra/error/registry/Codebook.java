package com.patra.error.registry;

import com.patra.error.core.ErrorCode;

import java.util.*;

/**
 * 错误码登记薄：合并多个来源，提供查询/校验与导出。
 */
public final class Codebook {

    private final Map<String, CodebookEntry> entries = new TreeMap<>();

    public Codebook() {}

    /** 注册或覆盖一条错误码 */
    public void register(CodebookEntry entry) {
        Objects.requireNonNull(entry, "entry");
        entries.put(entry.code().toString(), entry);
    }

    /** 批量注册 */
    public void registerAll(Collection<CodebookEntry> list) {
        if (list == null) return;
        list.forEach(this::register);
    }

    /** 查询 */
    public Optional<CodebookEntry> find(ErrorCode code) {
        return Optional.ofNullable(entries.get(code.toString()));
    }

    /** 全部只读视图 */
    public Map<String, CodebookEntry> all() {
        return Collections.unmodifiableMap(entries);
    }

    /** 合并另一本登记薄（右侧覆盖左侧） */
    public Codebook merge(Codebook other) {
        if (other == null) return this;
        var merged = new Codebook();
        merged.entries.putAll(this.entries);
        merged.entries.putAll(other.entries);
        return merged;
    }

    /** 校验：所有登记的 code 是否满足格式（返回不合法列表） */
    public List<String> validateLiterals() {
        List<String> invalid = new ArrayList<>();
        for (var key : entries.keySet()) {
            try {
                ErrorCode.of(key);
            } catch (IllegalArgumentException ex) {
                invalid.add(key);
            }
        }
        return invalid;
    }
}
