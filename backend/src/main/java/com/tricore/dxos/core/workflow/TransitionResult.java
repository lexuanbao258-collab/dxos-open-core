package com.tricore.dxos.core.workflow;

/** Validated transition proposal; success becomes durable only after the port commits it. */
public final class TransitionResult {
    private final WorkflowInstance previousInstance;
    private final WorkflowInstance instance;
    private final TransitionRecord record;

    TransitionResult(WorkflowInstance previousInstance, WorkflowInstance instance, TransitionRecord record) {
        this.previousInstance = previousInstance;
        this.instance = instance;
        this.record = record;
    }

    public WorkflowInstance previousInstance() { return previousInstance; }
    public WorkflowInstance instance() { return instance; }
    public TransitionRecord record() { return record; }
    public long expectedVersion() { return previousInstance.runtimeVersion(); }
}
