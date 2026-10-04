package com.tricore.dxos.request.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTest {
    @Test
    void newRequestHasNewStatusAndMatchingTimestamps() {
        Request request = new Request("Repair printer", "Printer is offline", "IT_SUPPORT");

        assertThat(request.getStatus()).isEqualTo(RequestStatus.NEW);
        assertThat(request.getCreatedAt()).isNotNull();
        assertThat(request.getUpdatedAt()).isEqualTo(request.getCreatedAt());
    }
}
