package com.cachewraith.blog_post_api_spring.integration.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

    /** Envelope sender. Must be an address the SMTP host is willing to send as. */
    private String from = "no-reply@localhost";

    private String appName = "Blog";

    /** How long recipients are told the code remains valid. Cosmetic; OtpService owns the real TTL. */
    private int otpTtlMinutes = 10;

    /**
     * Writes OTP codes to the log when no mail provider is configured, so registration is testable
     * locally.
     *
     * <p>Off by default and it must stay off anywhere real: a code in a log is a code available to
     * anyone who can read logs (OWASP A09). Set it in the dev profile only.
     */
    private boolean logCodes = false;
}
