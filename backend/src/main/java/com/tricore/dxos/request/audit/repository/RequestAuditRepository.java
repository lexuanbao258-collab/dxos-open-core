package com.tricore.dxos.request.audit.repository;

import com.tricore.dxos.request.audit.domain.RequestAudit;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

public interface RequestAuditRepository extends Repository<RequestAudit, UUID> {
    RequestAudit save(RequestAudit audit);
    List<RequestAudit> findAllByRequestIdOrderByCreatedAtAscIdAsc(UUID requestId);
}
