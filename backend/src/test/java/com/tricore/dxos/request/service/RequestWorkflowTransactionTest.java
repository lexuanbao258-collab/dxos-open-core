package com.tricore.dxos.request.service;

import com.tricore.dxos.request.domain.InvalidRequestTransitionException;
import com.tricore.dxos.request.domain.Request;
import com.tricore.dxos.request.audit.service.RequestAuditService;
import com.tricore.dxos.request.domain.RequestAction;
import com.tricore.dxos.request.domain.RequestStatus;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.repository.RequestRepository;
import com.tricore.dxos.request.repository.RequestStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

// Exercises Spring transaction advice with mocks; PostgreSQL rollback/concurrency remain runtime checks.
@ExtendWith(MockitoExtension.class)
class RequestWorkflowTransactionTest {
    @Mock private RequestAuditService audit;
    @Mock private RequestRepository requests;
    @Mock private RequestStatusHistoryRepository history;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transaction;
    private RequestWorkflowService service;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void proxyRealServiceWithSpringTransactionAdvice() {
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        ProxyFactory factory = new ProxyFactory(new RequestWorkflowService(requests, history, audit));
        factory.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        service = (RequestWorkflowService) factory.getProxy();
    }

    @ParameterizedTest
    @EnumSource(RequestAction.class)
    void eachActionSavesRequestAndHistoryBeforeCommittingSingleTransaction(RequestAction action) {
        Request request = requestBefore(action);
        when(requests.findById(id)).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenReturn(request);

        invoke(action);

        var calls = inOrder(transactionManager, requests, history, audit);
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        calls.verify(transactionManager).getTransaction(definition.capture());
        calls.verify(requests).findById(id);
        calls.verify(requests).saveAndFlush(request);
        calls.verify(history).save(any());
        calls.verify(audit).record(eq(id), any(), eq("anonymous"), isNull(), any());
        calls.verify(transactionManager).commit(transaction);
        assertThat(definition.getValue().isReadOnly()).isFalse();
        assertThat(definition.getValue().getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void historyInsertFailureRollsBackTransactionAfterRequestFlush() {
        Request request = requestBefore(RequestAction.ASSIGN);
        when(requests.findById(id)).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenReturn(request);
        when(history.save(any())).thenThrow(new DataIntegrityViolationException("history insert failed"));

        assertThatThrownBy(() -> service.assign(id, "it-user-001", "anonymous")).isInstanceOf(DataIntegrityViolationException.class);
        verify(requests).saveAndFlush(request);
        verify(transactionManager).rollback(transaction);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void invalidTransitionRollsBackWithoutSavingAnything() {
        Request request = requestBefore(RequestAction.ASSIGN);
        when(requests.findById(id)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.close(id, "anonymous")).isInstanceOf(InvalidRequestTransitionException.class);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.NEW);
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(history, audit);
        verify(transactionManager).rollback(transaction);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void optimisticConflictRollsBackWithoutHistoryInsert() {
        Request request = requestBefore(RequestAction.ASSIGN);
        when(requests.findById(id)).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenThrow(new ObjectOptimisticLockingFailureException(Request.class, id));

        assertThatThrownBy(() -> service.assign(id, "it-user-001", "anonymous"))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verifyNoInteractions(history, audit);
        verify(transactionManager).rollback(transaction);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void historyRunsInReadOnlyTransaction() {
        when(requests.findById(id)).thenReturn(Optional.of(requestBefore(RequestAction.ASSIGN)));
        when(history.findAllByRequestIdOrderByChangedAtAscIdAsc(id)).thenReturn(List.of());

        service.history(id);

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly()).isTrue();
        verify(transactionManager).commit(transaction);
    }

    private RequestResponse invoke(RequestAction action) {
        return switch (action) {
            case ASSIGN -> service.assign(id, "it-user-001", "anonymous");
            case START -> service.start(id, "anonymous");
            case RESOLVE -> service.resolve(id, "Restarted print service", "anonymous");
            case CONFIRM -> service.confirm(id, "anonymous");
            case CLOSE -> service.close(id, "anonymous");
        };
    }

    private Request requestBefore(RequestAction action) {
        Request request = new Request("Printer", "Offline", "IT_SUPPORT");
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "version", 0L);
        if (action == RequestAction.ASSIGN) return request;
        request.assign("it-user-001");
        if (action == RequestAction.START) return request;
        request.start();
        if (action == RequestAction.RESOLVE) return request;
        request.resolve("Restarted print service");
        if (action == RequestAction.CONFIRM) return request;
        request.confirm();
        return request;
    }
}
