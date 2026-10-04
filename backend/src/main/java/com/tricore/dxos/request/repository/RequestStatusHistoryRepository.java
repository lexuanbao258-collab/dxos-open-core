package com.tricore.dxos.request.repository;

import com.tricore.dxos.request.domain.RequestStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RequestStatusHistoryRepository extends JpaRepository<RequestStatusHistory, UUID> {
}
