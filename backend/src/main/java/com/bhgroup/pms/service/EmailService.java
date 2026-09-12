package com.bhgroup.pms.service;

import com.bhgroup.pms.config.AppProperties;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.context.Context;

/**
 * Builds the Thymeleaf context for each transactional email and hands it to
 * {@link EmailDispatcher}. The actual SMTP send is never allowed to affect
 * the caller: {@link #send} defers dispatch until the caller's transaction
 * (if any) has committed - so a failed booking/user-save never leaves an
 * email already sent for something that got rolled back - and
 * EmailDispatcher's own try/catch guarantees an SMTP or template failure is
 * only ever logged, never thrown back at a business flow.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailDispatcher emailDispatcher;
    private final AppProperties appProperties;

    public void sendPasswordResetEmail(String toEmail, String firstName, String rawToken, long expirationMinutes) {
        Context context = new Context();
        context.setVariable("appName", appProperties.getName());
        context.setVariable("firstName", firstName);
        context.setVariable("resetUrl", appProperties.getBaseUrl() + "/reset-password?token=" + rawToken);
        context.setVariable("expirationMinutes", expirationMinutes);

        send(toEmail, "Resetare parolă - " + appProperties.getName(), "email/password-reset-email", context);
    }

    public void sendUserInviteEmail(String toEmail, String firstName, String roleLabel, String rawToken,
                                     long expirationMinutes) {
        Context context = new Context();
        context.setVariable("appName", appProperties.getName());
        context.setVariable("firstName", firstName);
        context.setVariable("roleLabel", roleLabel);
        context.setVariable("inviteUrl", appProperties.getBaseUrl() + "/accept-invite/" + rawToken);
        context.setVariable("expirationDays", Math.max(1, expirationMinutes / 60 / 24));

        send(toEmail, "Ai fost invitat în " + appProperties.getName(), "email/user-invite-email", context);
    }

    public void sendBookingConfirmationEmail(String toEmail, String firstName, String propertyName,
                                              String checkInDate, String checkOutDate, String managementToken) {
        Context context = new Context();
        context.setVariable("appName", appProperties.getName());
        context.setVariable("firstName", firstName);
        context.setVariable("propertyName", propertyName);
        context.setVariable("checkInDate", checkInDate);
        context.setVariable("checkOutDate", checkOutDate);
        context.setVariable("manageUrl", appProperties.getBaseUrl() + "/manage-booking/" + managementToken);

        send(toEmail, "Cererea ta de rezervare - " + appProperties.getName(), "email/booking-confirmation-email", context);
    }

    public void sendPaymentConfirmedEmail(String toEmail, String firstName, String propertyName,
                                           String checkInDate, String checkOutDate, String amount,
                                           String currency, String managementToken) {
        Context context = new Context();
        context.setVariable("appName", appProperties.getName());
        context.setVariable("firstName", firstName);
        context.setVariable("propertyName", propertyName);
        context.setVariable("checkInDate", checkInDate);
        context.setVariable("checkOutDate", checkOutDate);
        context.setVariable("amount", amount);
        context.setVariable("currency", currency);
        context.setVariable("manageUrl", appProperties.getBaseUrl() + "/manage-booking/" + managementToken);

        send(toEmail, "Rezervare confirmată - " + appProperties.getName(), "email/payment-confirmed-email", context);
    }

    public void sendCheckinInstructionsEmail(String toEmail, String firstName, String propertyName,
                                              String checkInDate, String checkInTime, String address,
                                              String accessCode, String managementToken) {
        Context context = new Context();
        context.setVariable("appName", appProperties.getName());
        context.setVariable("firstName", firstName);
        context.setVariable("propertyName", propertyName);
        context.setVariable("checkInDate", checkInDate);
        context.setVariable("checkInTime", checkInTime);
        context.setVariable("address", address);
        context.setVariable("accessCode", accessCode);
        context.setVariable("manageUrl", appProperties.getBaseUrl() + "/manage-booking/" + managementToken);

        send(toEmail, "Instrucțiuni de check-in - " + appProperties.getName(),
                "email/checkin-instructions-email", context);
    }

    public void sendMaintenanceAlertEmail(String toEmail, String firstName, String propertyName,
                                           String ticketTitle, String ticketDescription) {
        Context context = new Context();
        context.setVariable("appName", appProperties.getName());
        context.setVariable("firstName", firstName);
        context.setVariable("propertyName", propertyName);
        context.setVariable("ticketTitle", ticketTitle);
        context.setVariable("ticketDescription", ticketDescription);

        send(toEmail, "Problemă critică la " + propertyName + " - " + appProperties.getName(),
                "email/maintenance-alert-email", context);
    }

    public void sendNewMessageEmail(String toEmail, String firstName, String propertyName,
                                     String messageBody, String managementToken) {
        Context context = new Context();
        context.setVariable("appName", appProperties.getName());
        context.setVariable("firstName", firstName);
        context.setVariable("propertyName", propertyName);
        context.setVariable("messageBody", messageBody);
        context.setVariable("manageUrl", appProperties.getBaseUrl() + "/manage-booking/" + managementToken);

        send(toEmail, "Mesaj nou despre rezervarea ta - " + appProperties.getName(),
                "email/new-message-email", context);
    }

    public void sendNewLeadAlertEmail(String toEmail, String adminFirstName, String leadFullName,
                                       String leadEmail, String leadPhone, String leadTypeLabel) {
        Context context = new Context();
        context.setVariable("appName", appProperties.getName());
        context.setVariable("firstName", adminFirstName);
        context.setVariable("leadFullName", leadFullName);
        context.setVariable("leadEmail", leadEmail);
        context.setVariable("leadPhone", leadPhone);
        context.setVariable("leadTypeLabel", leadTypeLabel);
        context.setVariable("leadsUrl", appProperties.getBaseUrl() + "/dashboard/leads");

        send(toEmail, leadTypeLabel + " - " + appProperties.getName(),
                "email/new-lead-alert-email", context);
    }

    public void sendAssistantHandoffEmail(String toEmail, String adminFirstName, String guestName,
                                           String preview, UUID chatId) {
        Context context = new Context();
        context.setVariable("appName", appProperties.getName());
        context.setVariable("firstName", adminFirstName);
        context.setVariable("guestName", guestName);
        context.setVariable("preview", preview);
        context.setVariable("chatUrl", appProperties.getBaseUrl() + "/dashboard/assistant-chats/" + chatId);

        send(toEmail, "Cerere de asistență live - " + appProperties.getName(),
                "email/assistant-handoff-alert-email", context);
    }

    /**
     * If the caller is inside an active Spring transaction, dispatch is
     * deferred to that transaction's afterCommit callback - so a caller like
     * "save the user, then email the invite" never sends an email for a row
     * that later fails to commit. With no active transaction (a caller
     * outside any @Transactional method, or a unit test with no Spring
     * transaction context), dispatch happens immediately, matching the
     * previous behavior.
     */
    private void send(String toEmail, String subject, String template, Context context) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emailDispatcher.dispatch(toEmail, subject, template, context);
                }
            });
        } else {
            emailDispatcher.dispatch(toEmail, subject, template, context);
        }
    }
}
