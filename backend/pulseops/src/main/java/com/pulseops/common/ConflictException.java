package com.pulseops.common;

/** A request that is well-formed but conflicts with the current state (stale version, invalid transition). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
