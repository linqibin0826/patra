package org.postgresql.util;

/**
 * 测试专用轻量 PGobject 模拟，实现 value 字段访问。
 */
public class PGobject {

    private String type;
    private String value;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
