package com.alrex.parcool.compat;

// Opaque handle to a specific Sable sub-level.  The underlying SubLevelAccess is stored as
// Object so this class's signature is Sable-class-free — vanilla-only builds can reference
// SubLevelHandle without triggering Sable classloading.  Only SableCompatImpl unpacks it.
public final class SubLevelHandle {
    final Object sla;

    SubLevelHandle(Object sla) {
        this.sla = sla;
    }
}
