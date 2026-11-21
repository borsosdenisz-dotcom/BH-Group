package com.bhstays.pms.validation;

import com.bhstays.pms.common.exception.BadRequestException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/** Strict allow-list shared by request validation and the service layer. */
public final class OtaListingUrlPolicy {

    public static final int MAX_LENGTH = 500;

    private static final Set<String> AIRBNB_DOMAINS = Set.of(
            "airbnb.com", "airbnb.com.ro", "airbnb.co.uk", "airbnb.ca", "airbnb.com.au",
            "airbnb.ie", "airbnb.de", "airbnb.es", "airbnb.fr", "airbnb.it", "airbnb.nl",
            "airbnb.pt", "airbnb.at", "airbnb.ch", "airbnb.be", "airbnb.dk", "airbnb.se",
            "airbnb.no", "airbnb.pl", "airbnb.cz", "airbnb.hu", "airbnb.gr", "airbnb.com.tr",
            "airbnb.co.nz", "airbnb.com.br", "airbnb.mx", "airbnb.com.ar", "airbnb.cl",
            "airbnb.jp", "airbnb.co.kr", "airbnb.co.in", "airbnb.com.sg", "airbnb.com.hk",
            "airbnb.com.tw", "airbnb.co.za", "airbnb.ae");
    private static final Set<String> BOOKING_DOMAINS = Set.of("booking.com");

    private OtaListingUrlPolicy() {
    }

    public static boolean isValid(String value, OtaPlatform platform) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            Set<String> allowedDomains = platform == OtaPlatform.AIRBNB ? AIRBNB_DOMAINS : BOOKING_DOMAINS;
            return allowedDomains.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    public static String normalizeOrThrow(String value, OtaPlatform platform) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!isValid(normalized, platform)) {
            throw new BadRequestException("URL-ul " + platform.displayName()
                    + " trebuie să fie HTTPS și să folosească un domeniu oficial "
                    + platform.displayName() + ".");
        }
        return normalized;
    }
}
