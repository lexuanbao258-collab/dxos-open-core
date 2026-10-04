package com.tricore.dxos.request.audit;

import com.tricore.dxos.request.audit.domain.RequestAuditAction;
import com.tricore.dxos.request.audit.service.RequestAuditService;
import com.tricore.dxos.request.dto.CreateRequestDto;
import com.tricore.dxos.request.repository.RequestRepository;
import com.tricore.dxos.request.service.RequestService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestCreationAuditTransactionTest {
    @Test
    void auditFailureRollsBackCreationTransaction() {
        var repository = mock(RequestRepository.class);
        var audit = mock(RequestAuditService.class);
        var manager = mock(PlatformTransactionManager.class);
        var transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(transaction);
        UUID id = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(call -> {
            var request = call.getArgument(0);
            ReflectionTestUtils.setField(request, "id", id);
            return request;
        });
        doThrow(new DataIntegrityViolationException("audit failure")).when(audit)
                .record(eq(id), eq(RequestAuditAction.REQUEST_CREATED), eq("user-001"), isNull(), any());
        var factory = new ProxyFactory(new RequestService(repository, audit));
        factory.setProxyTargetClass(true);
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(advice);
        var service = (RequestService) factory.getProxy();
        assertThatThrownBy(() -> service.create(new CreateRequestDto("Printer", "Offline", "IT_SUPPORT"), "user-001"))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }
}
