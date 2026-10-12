package com.bhstays.pms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A demo (showcase) property was asked for something only a bookable one
 * can do: a quote, a booking or a payment. Always 409 with the same
 * message, whichever public path it came through.
 */
public class DemoPropertyException extends ApiException {

    public static final String ERROR_CODE = "DEMO_PROPERTY";
    public static final String MESSAGE = "Anunț demonstrativ, nu se poate rezerva.";

    public DemoPropertyException() {
        super(HttpStatus.CONFLICT, ERROR_CODE, MESSAGE);
    }
}
