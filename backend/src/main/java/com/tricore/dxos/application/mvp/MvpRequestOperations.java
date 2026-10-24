package com.tricore.dxos.application.mvp;

import java.util.List;
import java.util.UUID;

/**
 * To be implemented by guarded Application orchestration, NOT the legacy Request prototype.
 *
 * Contract requirements before registration as LIVE:
 *  - verified CurrentPrincipal, never caller-provided requester/actor;
 *  - operation-specific exact authority AND authoritative request/tenant ACL;
 *  - requester/coordinator/current-assignee business eligibility;
 *  - list/detail/history resource and field/event filtering;
 *  - assignment optimistic concurrency, Workflow expectedVersion and final write guard;
 *  - consistent business facts/Workflow with safe unknown-commit reconciliation.
 *
 * No permissive implementation or Spring bean is provided by this review scaffold.
 */
public interface MvpRequestOperations {
    MvpRequestDtos.Detail create(MvpRequestDtos.Create input);
    List<MvpRequestDtos.Summary> listVisible();
    MvpRequestDtos.Detail getVisible(UUID id);
    MvpRequestDtos.Detail triage(UUID id, MvpRequestDtos.VersionedAction input);
    MvpRequestDtos.Detail assign(UUID id, MvpRequestDtos.Assign input);
    MvpRequestDtos.Detail start(UUID id, MvpRequestDtos.VersionedAction input);
    MvpRequestDtos.Detail resolve(UUID id, MvpRequestDtos.Resolve input);
    MvpRequestDtos.Detail confirm(UUID id, MvpRequestDtos.VersionedAction input);
    List<MvpRequestDtos.HistoryEvent> historyVisible(UUID id);
}
