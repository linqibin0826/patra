package com.patra.registry.domain.exception.dictionary;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.registry.domain.exception.RegistryException;

import java.util.Set;

/**
 * 字典仓储操作失败时抛出的异常（领域层）。
 *
 * <p>代表基础设施层错误，例如数据库连接问题、SQL 执行失败、数据访问异常等；
 * 在领域层定义仓储失败的契约，而不与具体基础设施实现耦合。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryRepositoryException extends RegistryException implements HasErrorTraits {
    
    /** 发生异常时执行的仓储操作名称 */
    private final String operation;
    
    /** 与失败操作相关的字典类型编码（如适用） */
    private final String typeCode;
    
    /** 与失败操作相关的字典项编码（如适用） */
    private final String itemCode;
    
    /** 构造函数（消息）。 */
    public DictionaryRepositoryException(String message) {
        super(message);
        this.operation = null;
        this.typeCode = null;
        this.itemCode = null;
    }
    
    /** 构造函数（消息 + 原因）。 */
    public DictionaryRepositoryException(String message, Throwable cause) {
        super(message, cause);
        this.operation = null;
        this.typeCode = null;
        this.itemCode = null;
    }
    
    /** 构造函数（消息 + 操作名 + 原因）。 */
    public DictionaryRepositoryException(String message, String operation, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.typeCode = null;
        this.itemCode = null;
    }
    
    /** 构造函数（完整上下文）。 */
    public DictionaryRepositoryException(String message, String operation, 
                                       String typeCode, String itemCode, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /** 获取失败的仓储操作名称。 */
    public String getOperation() {
        return operation;
    }
    
    /** 获取相关的字典类型编码（如有）。 */
    public String getTypeCode() {
        return typeCode;
    }
    
    /** 获取相关的字典项编码（如有）。 */
    public String getItemCode() {
        return itemCode;
    }
    
    /** 返回错误特征：仓储异常通常归类为 DEP_UNAVAILABLE。 */
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.DEP_UNAVAILABLE);
    }
}
