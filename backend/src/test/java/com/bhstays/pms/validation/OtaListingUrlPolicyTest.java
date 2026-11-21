package com.bhstays.pms.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class OtaListingUrlPolicyTest {

    @ParameterizedTest
    @MethodSource("validUrls")
    void acceptsOnlyOfficialHttpsUrls(String url, OtaPlatform platform) {
        assertThat(OtaListingUrlPolicy.isValid(url, platform)).isTrue();
    }

    static Stream<Arguments> validUrls() {
        return Stream.of(
                Arguments.of("https://www.airbnb.com/rooms/123", OtaPlatform.AIRBNB),
                Arguments.of("https://airbnb.com.ro/rooms/123?check_in=2030-01-01", OtaPlatform.AIRBNB),
                Arguments.of("https://www.booking.com/hotel/ro/example.ro.html", OtaPlatform.BOOKING),
                Arguments.of("https://secure.booking.com/myreservations.html", OtaPlatform.BOOKING),
                Arguments.of(null, OtaPlatform.AIRBNB),
                Arguments.of("  ", OtaPlatform.BOOKING));
    }

    @ParameterizedTest
    @MethodSource("invalidUrls")
    void rejectsUnsafeOrLookalikeUrls(String url, OtaPlatform platform) {
        assertThat(OtaListingUrlPolicy.isValid(url, platform)).isFalse();
    }

    static Stream<Arguments> invalidUrls() {
        return Stream.of(
                Arguments.of("http://www.airbnb.com/rooms/123", OtaPlatform.AIRBNB),
                Arguments.of("javascript:alert(1)", OtaPlatform.AIRBNB),
                Arguments.of("data:text/html,hello", OtaPlatform.BOOKING),
                Arguments.of("/rooms/123", OtaPlatform.AIRBNB),
                Arguments.of("https://airbnb.com.evil.example/rooms/123", OtaPlatform.AIRBNB),
                Arguments.of("https://booking.com.evil.example/hotel/ro/x", OtaPlatform.BOOKING),
                Arguments.of("https://booking.example/hotel/ro/x", OtaPlatform.BOOKING),
                Arguments.of("https://booking.com@evil.example/hotel/ro/x", OtaPlatform.BOOKING),
                Arguments.of("https://www.airbnb.com:8443/rooms/123", OtaPlatform.AIRBNB));
    }
}
