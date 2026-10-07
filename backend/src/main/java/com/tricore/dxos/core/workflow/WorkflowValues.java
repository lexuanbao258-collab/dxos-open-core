package com.tricore.dxos.core.workflow;

/** Local structural validation; deliberately not a shared Core abstraction. */
final class WorkflowValues {
    private WorkflowValues() {}

    static String text(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static long nonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
