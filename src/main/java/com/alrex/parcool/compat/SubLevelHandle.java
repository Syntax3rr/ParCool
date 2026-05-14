package com.alrex.parcool.compat;

// Opaque handle to a Sable sub-level. The Sable type is held as Object so vanilla-only
// builds can reference SubLevelHandle without classloading Sable. Only SableCompatImpl
// unpacks the underlying SubLevelAccess.
public final class SubLevelHandle {
    final Object sla;

    SubLevelHandle(Object sla) {
        this.sla = sla;
    }
}
