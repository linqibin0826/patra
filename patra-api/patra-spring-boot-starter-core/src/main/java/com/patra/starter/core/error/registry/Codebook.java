package com.patra.starter.core.error.registry;

import cn.hutool.core.collection.CollUtil;
import com.patra.common.error.core.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 错误码册，负责管理和查询错误码条目信息。
 * 
 * <p>主要功能：
 * <ul>
 *   <li>注册和管理错误码条目</li>
 *   <li>支持多源合并（后注册覆盖先注册）</li>
 *   <li>提供错误码查询和校验功能</li>
 *   <li>导出所有注册条目的只读视图</li>
 * </ul>
 * 
 * <p>使用示例：
 * <pre>{@code
 * Codebook codebook = new Codebook();
 * 
 * // 注册单个条目
 * ErrorCode errorCode = ErrorCode.of("REG-C0101");
 * CodebookEntry entry = new CodebookEntry(errorCode, "Parameter missing", 422, null, "team", null);
 * codebook.register(entry);
 * 
 * // 查询条目
 * Optional<CodebookEntry> found = codebook.find(errorCode);
 * 
 * // 合并其他错误码册
 * Codebook another = new Codebook();
 * codebook.merge(another);
 * }</pre>
 * 
 * <p>线程安全性：
 * <ul>
 *   <li>读操作线程安全</li>
 *   <li>写操作需要外部同步</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 * @see CodebookEntry
 * @see ErrorCode
 */
@Slf4j
public final class Codebook {

    /**
     * 错误码条目存储映射，键为错误码字符串形式，值为条目对象。
     * 使用 TreeMap 保证输出顺序的一致性。
     */
    private final Map<String, CodebookEntry> errorCodeEntries = new TreeMap<>();

    /**
     * 默认构造器，创建空的错误码册。
     */
    public Codebook() {
    }

    /**
     * 注册或覆盖一个错误码条目。
     * 
     * @param entry 错误码条目，不能为 null
     * @throws NullPointerException 如果 entry 为 null
     */
    public void register(CodebookEntry entry) {
        Objects.requireNonNull(entry, "CodebookEntry must not be null");
        errorCodeEntries.put(entry.code().toString(), entry);
        log.debug("Registered error code entry: {}", entry.code());
    }

    /**
     * 批量注册错误码条目。
     * 
     * @param entryList 错误码条目列表，可以为 null 或空
     */
    public void registerAll(Collection<CodebookEntry> entryList) {
        if (CollUtil.isNotEmpty(entryList)) {
            entryList.forEach(this::register);
        }
    }

    /**
     * 查询指定错误码的条目信息。
     * 
     * @param errorCode 错误码对象
     * @return 包装的错误码条目，如果不存在则返回 empty
     */
    public Optional<CodebookEntry> find(ErrorCode errorCode) {
        return Optional.ofNullable(errorCodeEntries.get(errorCode.toString()));
    }

    /**
     * 获取所有错误码条目的只读视图。
     * 
     * @return 错误码条目的不可变映射，键为错误码字符串，值为条目对象
     */
    public Map<String, CodebookEntry> all() {
        return Collections.unmodifiableMap(errorCodeEntries);
    }

    /**
     * 合并另一个错误码册到当前册中。
     * 
     * <p>合并策略：如果存在相同的错误码，后合并的条目会覆盖先存在的条目。
     * 
     * @param otherCodebook 要合并的错误码册，可以为 null
     * @return 当前错误码册实例，支持链式调用
     */
    public Codebook merge(Codebook otherCodebook) {
        if (otherCodebook != null) {
            this.errorCodeEntries.putAll(otherCodebook.errorCodeEntries);
            log.debug("Merged {} entries from another codebook", otherCodebook.errorCodeEntries.size());
        }
        return this;
    }

    /**
     * 校验所有已注册的错误码格式。
     * 
     * <p>检查所有错误码字符串是否符合 ErrorCode 的格式要求。
     * 
     * @return 不合法的错误码字符串列表，如果全部合法则返回空列表
     */
    public List<String> validateLiterals() {
        List<String> invalidCodes = new ArrayList<>();
        for (String errorCodeKey : errorCodeEntries.keySet()) {
            try {
                ErrorCode.of(errorCodeKey);
            } catch (IllegalArgumentException ex) {
                invalidCodes.add(errorCodeKey);
                log.warn("Invalid error code format detected: {}", errorCodeKey);
            }
        }
        return invalidCodes;
    }
}
