package com.pulseops.rules;

public enum Operator {
    GT("above"),
    LT("below");

    private final String word;

    Operator(String word) {
        this.word = word;
    }

    public String word() {
        return word;
    }

    public boolean breaches(double value, double threshold) {
        return this == GT ? value > threshold : value < threshold;
    }

    /** The "worse" of two values for this operator, used to track an incident's peak. */
    public double worse(double a, double b) {
        return this == GT ? Math.max(a, b) : Math.min(a, b);
    }
}
