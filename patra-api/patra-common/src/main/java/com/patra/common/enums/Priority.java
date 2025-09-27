package com.patra.common.enums;

public enum Priority {
    LOWEST(90),
    LOWER(80),
    LOW(70),
    NORMAL(50),
    HIGH(30),
    HIGHER(20),
    HIGHEST(10);

    private final int queueValue;

    Priority(int queueValue) {
        this.queueValue = queueValue;
    }

    public int queueValue() {
        return queueValue;
    }
}
