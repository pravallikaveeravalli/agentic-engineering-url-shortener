package agentic.shortener.config;

import agentic.shortener.orchestration.api.RunInspectionQuery;
import agentic.shortener.orchestration.gates.GateOutcomeHandler;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Control-plane wiring. Task T067.
 *
 * <p>A separate class from {@link PersistenceConfiguration}, not an addition to it, because that class's
 * own javadoc states the two planes stay separable only if the wiring reflects the boundary too — one
 * {@code @Configuration} class per plane is what keeps "the shortener is demonstrable with the
 * orchestration engine switched off" (T014's rule, ADR-006) true of the wiring itself, not only of the
 * code it wires.
 *
 * <p>{@code config} is not one of T014's four forbidden packages (domain, application, delivery,
 * persistence) — it is the one place allowed to know both planes, the same role
 * {@link PersistenceConfiguration} already plays for the application plane's own repositories. Reuses the
 * {@link ConnectionSource} bean that class already exposes, bridging Spring's {@code DataSource} once for
 * both planes rather than a second time here.
 */
@Configuration
public class OrchestrationConfiguration {

    @Bean
    public RunInspectionQuery runInspectionQuery(ConnectionSource connections) {
        return new RunInspectionQuery(connections);
    }

    @Bean
    public JdbcRunStore jdbcRunStore(ConnectionSource connections, Clock clock) {
        return new JdbcRunStore(connections, clock);
    }

    @Bean
    public GateStore gateStore(ConnectionSource connections, Clock clock) {
        return new GateStore(connections, clock);
    }

    @Bean
    public GateOutcomeHandler gateOutcomeHandler(JdbcRunStore jdbcRunStore, GateStore gateStore,
                                                 ConnectionSource connections) {
        return new GateOutcomeHandler(jdbcRunStore, gateStore, connections);
    }
}
