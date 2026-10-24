package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import com.tricore.dxos.gateway.helpdesk.HelpdeskController;
import static org.assertj.core.api.Assertions.*;

class HelpdeskConfigurationTest {
    @Test void disabledByDefault() {
        try(var context=new AnnotationConfigApplicationContext(HelpdeskConfiguration.class,HelpdeskController.class)) {
            assertThat(context.getBeansOfType(HelpdeskService.class)).isEmpty();
            assertThat(context.getBeansOfType(HelpdeskController.class)).isEmpty();
        }
    }
    @Test void candidateCannotTargetSharedDatabaseOrPublicListener() {
        HelpdeskConfiguration.requireDisposable("jdbc:postgresql://127.0.0.1:55439/helpdesk_disposable_test","helpdesk_runtime_test","127.0.0.1");
        for(String url:new String[]{"jdbc:postgresql://shared.example:5432/helpdesk_disposable_test","jdbc:postgresql://127.0.0.1:5432/postgres",
                "jdbc:postgresql://127.0.0.1:55439/helpdesk_disposable_test?options=anything"})
            assertThatThrownBy(() -> HelpdeskConfiguration.requireDisposable(url,"helpdesk_runtime_test","127.0.0.1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HelpdeskConfiguration.requireDisposable("jdbc:postgresql://127.0.0.1:55439/helpdesk_disposable_test","postgres","127.0.0.1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HelpdeskConfiguration.requireDisposable("jdbc:postgresql://127.0.0.1:55439/helpdesk_disposable_test","helpdesk_runtime_test","0.0.0.0")).isInstanceOf(IllegalArgumentException.class);
    }
}
