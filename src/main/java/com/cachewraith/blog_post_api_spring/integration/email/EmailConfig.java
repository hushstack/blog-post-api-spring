package com.cachewraith.blog_post_api_spring.integration.email;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Picks the mail implementation.
 *
 * <p>Boot only defines a {@code JavaMailSender} when {@code spring.mail.host} is set, so its
 * presence is the configuration signal — resolved here through an {@code ObjectProvider} and a
 * single bean method rather than a pair of {@code @ConditionalOnMissingBean} beans, whose
 * resolution order in user configuration is easy to get subtly wrong.
 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    @Bean
    public EmailService emailService(
            ObjectProvider<JavaMailSender> mailSender, EmailProperties properties) {
        JavaMailSender sender = mailSender.getIfAvailable();
        return sender == null
                ? new LoggingEmailService(properties)
                : new SmtpEmailService(sender, properties);
    }
}
