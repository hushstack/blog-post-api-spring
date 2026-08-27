package com.cachewraith.blog_post_api_spring.integration.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;

/** Sends through the configured SMTP host. Selected by {@link EmailConfig} when one is set. */
@Slf4j
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    @Override
    public void sendOtp(String recipient, String code, String purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom());
        message.setTo(recipient);
        message.setSubject(subjectFor(purpose));
        message.setText(bodyFor(code, purpose));

        try {
            mailSender.send(message);
            // Recipient and purpose only. The code never reaches the log (OWASP A09).
            log.info("Sent {} OTP mail to {}", purpose, recipient);
        } catch (MailException e) {
            // Rethrown so the listener nacks and the message dead-letters rather than being lost:
            // silently swallowing this would leave a user waiting for a mail nobody will resend.
            log.error("Failed to send {} OTP mail to {}", purpose, recipient, e);
            throw e;
        }
    }

    private String subjectFor(String purpose) {
        return "RESET_PASSWORD".equals(purpose)
                ? properties.getAppName() + " password reset code"
                : properties.getAppName() + " verification code";
    }

    private String bodyFor(String code, String purpose) {
        String action =
                "RESET_PASSWORD".equals(purpose)
                        ? "reset your password"
                        : "verify your email address";
        return """
                Your %s code to %s is:

                    %s

                It expires in %d minutes. If you did not request it, ignore this message.
                """
                .formatted(properties.getAppName(), action, code, properties.getOtpTtlMinutes());
    }
}
