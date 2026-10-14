package com.phonecare.model;

public enum TroubleshootOutcome {
    SUCCESSFUL,
    PARTIALLY_SUCCESSFUL,
    UNSUCCESSFUL,
    UNKNOWN,
    NOT_ATTEMPTED;

    public static TroubleshootOutcome fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return UNKNOWN;
        }
        String clean = value.trim().toUpperCase();
        return switch (clean) {
            case "SOLVED", "SUCCESSFUL", "SUCCESS" -> SUCCESSFUL;
            case "PARTIAL", "PARTIALLY_SUCCESSFUL" -> PARTIALLY_SUCCESSFUL;
            case "NOT_SOLVED", "UNSUCCESSFUL", "FAILED" -> UNSUCCESSFUL;
            case "SKIPPED", "NOT_ATTEMPTED" -> NOT_ATTEMPTED;
            default -> UNKNOWN;
        };
    }
}
