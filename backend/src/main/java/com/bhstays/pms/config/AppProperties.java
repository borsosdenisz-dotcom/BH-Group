package com.bhstays.pms.config;

import java.math.BigDecimal;
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
    private PricingAi pricingAi = new PricingAi();
    /**
     * Explicit launch gate for unauthenticated reservations. Stripe being
     * configured is necessary, but never turns public direct booking on by
     * itself. Defaults to false through both Java and application.yml.
     */
    private boolean publicDirectBookingEnabled = false;

    @Getter
    @Setter
    public static class Mail {
        /** Envelope sender. Must be a sender the SMTP provider has verified. */
        private String from;
        /**
         * Where replies go. The From address is a no-reply on the
         * DKIM-signed domain, which is what keeps mail out of spam, but a
         * guest hitting Reply should still reach a mailbox someone reads.
         */
        private String replyTo;
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
     * The AI pricing recommender. It calls the same Anthropic account as the
     * assistant (one API key, one base URL), but a pricing analysis is a
     * longer, heavier single request than a chat turn, so the model, token
     * ceiling and timeout are tuned separately.
     */
    @Getter
    @Setter
    public static class PricingAi {
        private String model;
        private int maxTokens;
        private long timeoutMs;
        private BigDecimal absoluteMinRon;
        private BigDecimal absoluteMaxRon;
        private BigDecimal minBaseRatio;
        private BigDecimal maxBaseRatio;
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
