package com.bhgroup.pms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String name;
    private String baseUrl;
    private String apiBaseUrl;
    private Mail mail = new Mail();
    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private Storage storage = new Storage();
    private Contact contact = new Contact();
    private Assistant assistant = new Assistant();
    private Stripe stripe = new Stripe();

    @Getter
    @Setter
    public static class Mail {
        private String from;
    }

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigins;
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenExpirationMs;
        private long refreshTokenExpirationMs;
        private String issuer;
    }

    @Getter
    @Setter
    public static class Security {
        private long emailVerificationTokenExpirationMinutes;
        private long passwordResetTokenExpirationMinutes;
        private long userInviteTokenExpirationMinutes;
        private int maxLoginAttempts;
        private long loginLockoutMinutes;
        private boolean refreshCookieSecure;
    }

    @Getter
    @Setter
    public static class Storage {
        private String uploadDir;
        private String publicBaseUrl;
    }

    @Getter
    @Setter
    public static class Contact {
        private String email;
        private String phone;
    }

    @Getter
    @Setter
    public static class Assistant {
        private String apiKey;
        private String model;
        private String baseUrl;
        private int maxTokens;
        private int maxHistoryMessages;
        private long timeoutMs;
        private int retentionDays;
    }

    /**
     * Stripe hosted Checkout. Card data never touches this server - the
     * payment form lives on Stripe's own domain, and we only ever see the
     * session/payment-intent ids that come back. Leaving {@code secretKey}
     * empty disables card payments entirely (no STRIPE gateway bean is
     * registered), and the site falls back to manual payment only.
     */
    @Getter
    @Setter
    public static class Stripe {
        private String secretKey;
        private String publishableKey;
        private String webhookSecret;
    }
}
