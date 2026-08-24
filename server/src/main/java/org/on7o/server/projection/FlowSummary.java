package org.on7o.server.projection;

/**
 * Which way money moves across one edge, taken over the whole history up to the
 * instant being drawn.
 */
public enum FlowSummary {

    /** No money passes directly between these two. */
    NONE,

    /** Money only ever comes in from this direction. */
    INFLOW,

    /** Money only ever goes out this way. */
    OUTFLOW,

    /** Money moves both ways. */
    BOTH
}
