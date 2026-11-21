package com.bhstays.pms.validation;

public enum OtaPlatform {
    AIRBNB("Airbnb"),
    BOOKING("Booking.com");

    private final String displayName;

    OtaPlatform(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
