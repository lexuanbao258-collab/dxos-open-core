package com.tricore.dxos.request.domain;

public class InvalidRequestTransitionException extends RuntimeException {
    public InvalidRequestTransitionException(RequestStatus currentStatus, RequestAction action) {
        super("Cannot perform " + action + " while Request status is " + currentStatus);
    }
}
