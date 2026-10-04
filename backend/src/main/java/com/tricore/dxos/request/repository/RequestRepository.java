package com.tricore.dxos.request.repository;

import com.tricore.dxos.request.domain.Request;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RequestRepository extends JpaRepository<Request, UUID> {
}
