package agentic.shortener.config;

import agentic.shortener.application.LinkService;
import agentic.shortener.domain.analytics.RedirectEventRepository;
import agentic.shortener.domain.creator.CreatorRepository;
import agentic.shortener.domain.idempotency.IdempotencyRepository;
import agentic.shortener.domain.link.ShortCodeGenerator;
import agentic.shortener.domain.link.ShortLinkRepository;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.JdbcCreatorRepository;
import agentic.shortener.persistence.JdbcIdempotencyRepository;
import agentic.shortener.persistence.JdbcRedirectEventRepository;
import agentic.shortener.persistence.JdbcShortLinkRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Clock;

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
    public LinkService linkService(ShortLinkRepository links, CreatorRepository creators,
                                  RedirectEventRepository events, ShortCodeGenerator codes,
                                  Clock clock) {
        return new LinkService(links, creators, events, codes, clock);
    }
}
