package com.kset.agent.core.protocol.json;

/** Stable protocol error codes emitted by the built-in agent-json:v1 codec. */
public enum AgentJsonV1ErrorCode {
    EMPTY_RESPONSE,
    MISSING_MARKERS,
    INVALID_MARKERS,
    MULTIPLE_ENVELOPES,
    OUTSIDE_CONTENT,
    INVALID_JSON_BOUNDARY,
    INVALID_JSON,
    UNSUPPORTED_ACTION,
    INVALID_SCHEMA,
    INVALID_PLAN,
    INVALID_TOOL_BATCH,
    INVALID_TOOL_CALL,
    INVALID_TOOL_ARGUMENTS,
    INVALID_CONFIRMATION,
    INVALID_METADATA,
    UNKNOWN_FIELD,
    MISSING_FIELD
}
