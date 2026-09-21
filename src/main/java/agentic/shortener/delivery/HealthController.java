package agentic.shortener.delivery;

import agentic.shortener.persistence.ConnectionSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Liveness and readiness. Task T032. FR-URL-015. ADR-012.
 *
 * <p><strong>The two are deliberately different, and the difference is the requirement.</strong>
 * FR-URL-015 makes liveness independent of store availability while readiness must fail when a
 * dependency is down. Conflating them is the classic health-check defect: an orchestrator restarts a
 * process whose store is merely unreachable, turning a dependency outage into a restart loop while the
 * process itself was fine.
 *
 * <p>Neither response carries a connection string, a credential or internal topology. A readiness body
 * that helpfully reported the JDBC URL would hand an unauthenticated caller the datasource host and
 * database name -- and both endpoints are unauthenticated by contract (`security: []`).
 */
@RestController
public class HealthController {

    private final ConnectionSource connections;

    public HealthController(ConnectionSource connections) {
        this.connections = connections;
    }

    /**
     * Liveness. Reports UP whenever the process can answer, and carries NO dependency checks.
     *
     * <p>If this consulted the store, a store outage would fail liveness and an orchestrator would
     * restart a healthy process -- the outcome FR-URL-015 separates the two probes to prevent.
     */
    @GetMapping("/health/live")
    public Map<String, Object> live() {
        return Map.of("status", "UP");
    }

    /**
     * Readiness. <strong>Fails closed</strong> (T018): if the store cannot be reached the instance
     * reports DOWN rather than accepting traffic it cannot serve.
     */
    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        boolean storeUp = storeReachable();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", storeUp ? "UP" : "DOWN");
        // Named checks, so an UP means something a reader can point at. The VALUES are UP/DOWN only --
        // never an error message, which is how a driver exception reaches an unauthenticated caller.
        body.put("checks", Map.of("store", storeUp ? "UP" : "DOWN"));

        return ResponseEntity.status(storeUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    private boolean storeReachable() {
        try (Connection c = connections.get()) {
            return c.isValid(2);
        } catch (Exception e) {
            // Swallowed deliberately, and only here: the failure is REPORTED as DOWN, and the
            // exception detail must not reach the response (T018, verbose error detail off).
            return false;
        }
    }
}
