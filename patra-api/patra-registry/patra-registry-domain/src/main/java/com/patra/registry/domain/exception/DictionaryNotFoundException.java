package com.patra.registry.domain.exception;

/**
 * Domain exception thrown when a requested dictionary type or item cannot be found.
 * This exception indicates that the requested dictionary resource does not exist
 * in the system or is not accessible due to business rules.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryNotFoundException extends RegistryNotFound {
    
    /** The dictionary type code associated with this exception, if applicable */
    private final String typeCode;
    
    /** The dictionary item code associated with this exception, if applicable */
    private final String itemCode;
    
    /**
     * Constructs a new dictionary not found exception for a missing type.
     * 
     * @param typeCode the dictionary type code that was not found
     */
    public DictionaryNotFoundException(String typeCode) {
        super(String.format("Dictionary type not found: %s", typeCode));
        this.typeCode = typeCode;
        this.itemCode = null;
    }
    
    /**
     * Constructs a new dictionary not found exception for a missing item.
     * 
     * @param typeCode the dictionary type code
     * @param itemCode the dictionary item code that was not found
     */
    public DictionaryNotFoundException(String typeCode, String itemCode) {
        super(String.format("Dictionary item not found: typeCode=%s, itemCode=%s", typeCode, itemCode));
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /**
     * Constructs a new dictionary not found exception with a custom message.
     * 
     * @param message the detail message explaining what was not found
     * @param typeCode the dictionary type code, if applicable
     * @param itemCode the dictionary item code, if applicable
     */
    public DictionaryNotFoundException(String message, String typeCode, String itemCode) {
        super(message);
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /**
     * Gets the dictionary type code associated with this exception.
     * 
     * @return the type code, or null if not applicable
     */
    public String getTypeCode() {
        return typeCode;
    }
    
    /**
     * Gets the dictionary item code associated with this exception.
     * 
     * @return the item code, or null if not applicable
     */
    public String getItemCode() {
        return itemCode;
    }
}