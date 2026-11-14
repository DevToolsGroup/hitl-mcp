package com.example.generalmcp.review;

public enum Phase {
    /** The user decides whether the tool may be called. */
    AWAITING_CALL_APPROVAL,
    /** The tool is running; nothing to decide. */
    EXECUTING,
    /** The user decides whether the result may be returned to the agent. */
    AWAITING_RESULT_APPROVAL
}
