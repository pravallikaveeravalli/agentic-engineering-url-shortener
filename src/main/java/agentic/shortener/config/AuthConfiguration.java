package agentic.shortener.config;

import agentic.shortener.delivery.auth.CreatorAuthFilter;
import agentic.shortener.domain.creator.CreatorRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/**
 * Where authentication applies, and — more importantly — where it does not. Task T052. FR-URL-018.
 *
 * <p><strong>Registered with explicit URL patterns, never auto-registered.</strong> A {@code Filter}
 * exposed as a bean is auto-registered by Spring Boot for {@code /*}, which would put it on the redirect
 * path with an internal opt-out. T044's guard is that the redirect path must not be <em>affected</em> by
 * credentials, and an opt-out inside a filter that runs on every request is one edit away from not opting
 * out. So the filter is constructed inside the registration and is deliberately not a bean of its own.
 *
 * <p><strong>The patterns are an allow-list of authenticated paths, not a deny-list of public ones.</strong>
 * A new public endpoint added later is public by default, which is the safe direction for a redirect
 * service. A new <em>authenticated</em> endpoint has to be named here, which is a visible decision.
 *
 * <p>{@code RedirectIT} asserts that no pattern registered here can match a short code.
 */
@Configuration
public class AuthConfiguration {

    /**
     * The paths that require a creator credential. FR-URL-018: creation and analytics retrieval.
     *
     * <p>Public and NOT listed: {@code GET /{shortCode}} (a short link that requires login is not a
     * short link) and the health probes (an orchestrator cannot hold a creator credential).
     */
    public static final List<String> AUTHENTICATED_PATTERNS = List.of("/v1/links", "/v1/links/*");

    @Bean
    public FilterRegistrationBean<CreatorAuthFilter> creatorAuthFilterRegistration(
            CreatorRepository creators, Clock clock) {
        FilterRegistrationBean<CreatorAuthFilter> registration =
                new FilterRegistrationBean<>(new CreatorAuthFilter(creators, clock));
        AUTHENTICATED_PATTERNS.forEach(registration::addUrlPatterns);
        registration.setName("creatorAuthFilter");
        return registration;
    }
}
