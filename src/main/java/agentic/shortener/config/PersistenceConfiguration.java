package agentic.shortener.config;

import agentic.shortener.application.CreateLinkUseCase;
import agentic.shortener.application.GetAnalyticsUseCase;
import agentic.shortener.application.IdempotencyResolver;
import agentic.shortener.application.LinkService;
import agentic.shortener.application.ResolveLinkUseCase;
import agentic.shortener.domain.analytics.AnalyticsRecordingPort;
import agentic.shortener.domain.analytics.RedirectEventRepository;
import agentic.shortener.domain.creator.CreatorRepository;
import agentic.shortener.domain.idempotency.IdempotencyRepository;
import agentic.shortener.domain.shortcode.ShortCodeGenerator;
import agentic.shortener.domain.link.ExpiryPolicy;
import agentic.shortener.domain.link.ShortLinkRepository;
import agentic.shortener.domain.validation.AbuseGuard;
import agentic.shortener.domain.validation.DestinationNormalizer;
import agentic.shortener.domain.validation.UrlSyntaxValidator;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.JdbcCreatorRepository;
import agentic.shortener.persistence.JdbcIdempotencyRepository;
import agentic.shortener.persistence.JdbcRedirectEventRepository;
import agentic.shortener.persistence.JdbcShortLinkRepository;
import agentic.shortener.persistence.analytics.TransactionalAnalyticsRecorder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * Wiring. Task T032.
 *
 * <p><strong>The only package that knows both sides.</strong> The domain declares repository interfaces
 * and never learns who implements them; the persistence package implements them and never learns who
 * calls them. This class is where the two meet, which is why `config` is excluded from the PVT-008
 * coverage denominator (T145) — it is declarative wiring with no branches worth covering, and that
 * exclusion is only legitimate because the class stays this thin.
 *
 * <p>Every bean is an interface type, so ADR-002's store choice stays reversible: swapping PostgreSQL
 * means adding another implementation and changing these five lines.
 */
@Configuration
public class PersistenceConfiguration {

    /**
     * Bridges Spring's {@link DataSource} to the domain-agnostic {@link ConnectionSource}.
     *
     * <p>The repositories take this one-method interface rather than a {@code DataSource} so that they
     * remain constructible in a test without a Spring context — which is what keeps the repository
     * integration tests out of the framework's startup cost.
     */
    @Bean
    public ConnectionSource connectionSource(DataSource dataSource) {
        return dataSource::getConnection;
    }

    @Bean
    public ShortLinkRepository shortLinkRepository(ConnectionSource connections) {
        return new JdbcShortLinkRepository(connections);
    }

    @Bean
    public CreatorRepository creatorRepository(ConnectionSource connections) {
        return new JdbcCreatorRepository(connections);
    }

    @Bean
    public IdempotencyRepository idempotencyRepository(ConnectionSource connections) {
        return new JdbcIdempotencyRepository(connections);
    }

    @Bean
    public RedirectEventRepository redirectEventRepository(ConnectionSource connections) {
        return new JdbcRedirectEventRepository(connections);
    }

    @Bean
    public ShortCodeGenerator shortCodeGenerator() {
        return new ShortCodeGenerator();
    }

    /**
     * The system clock, injected rather than called statically.
     *
     * <p>FR-URL-009's expiry rules and EC-036's idle-retention behaviour both need a test to control
     * time. A test that sleeps to advance a clock is slow and flaky at once, so the clock is a bean.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CreateLinkUseCase createLinkUseCase(ShortLinkRepository links, ShortCodeGenerator codes,
                                               Clock clock) {
        return new CreateLinkUseCase(links, codes, clock);
    }

    @Bean
    public IdempotencyResolver idempotencyResolver(IdempotencyRepository markers,
                                                  ShortLinkRepository links) {
        return new IdempotencyResolver(markers, links);
    }

    @Bean
    public DestinationNormalizer destinationNormalizer() {
        return new DestinationNormalizer();
    }

    @Bean
    public UrlSyntaxValidator urlSyntaxValidator() {
        return new UrlSyntaxValidator();
    }

    /**
     * EC-005 needs to know which hosts are ours. Configurable — unlike the scheme allow-list, which
     * must not be (FR-URL-004) — because the answer genuinely differs per deployment.
     *
     * <p>The failure direction is guarded at the type: {@link AbuseGuard} refuses an empty set, so a
     * missing or blank configuration is a startup failure rather than a check that silently passes
     * everything.
     */
    @Bean
    public AbuseGuard abuseGuard(@Value("${shortener.own-hosts}") String ownHosts) {
        return new AbuseGuard(new LinkedHashSet<>(Arrays.asList(ownHosts.split(","))));
    }

    /**
     * The single implementation of the analytics recording port (T048, T050).
     *
     * <p>A bean of the PORT type, not of the implementation. ADR-014's evolution ladder — the Postgres
     * table now, a queue or a stream later — is reachable only if nothing downstream knows which rung it
     * is on, and {@code AnalyticsPortBypassTest} keeps that true.
     */
    @Bean
    public AnalyticsRecordingPort analyticsRecordingPort(RedirectEventRepository events) {
        return new TransactionalAnalyticsRecorder(events);
    }

    @Bean
    public GetAnalyticsUseCase getAnalyticsUseCase(ShortLinkRepository links,
                                                  RedirectEventRepository events) {
        return new GetAnalyticsUseCase(links, events);
    }

    @Bean
    public ExpiryPolicy expiryPolicy(Clock clock) {
        return new ExpiryPolicy(clock);
    }

    @Bean
    public ResolveLinkUseCase resolveLinkUseCase(ShortLinkRepository links, Clock clock) {
        return new ResolveLinkUseCase(links, clock);
    }

    @Bean
    public LinkService linkService(ShortLinkRepository links, CreatorRepository creators,
                                  RedirectEventRepository events, IdempotencyRepository markers,
                                  CreateLinkUseCase createLink, IdempotencyResolver idempotency,
                                  DestinationNormalizer normalizer, UrlSyntaxValidator syntax,
                                  AbuseGuard abuse, ExpiryPolicy expiry, Clock clock) {
        return new LinkService(links, creators, events, markers, createLink, idempotency,
                normalizer, syntax, abuse, expiry, clock);
    }
}
