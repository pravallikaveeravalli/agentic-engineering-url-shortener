package agentic.shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single deployable carrying two internal planes: the URL shortener (application plane) and the
 * governed orchestration engine (control plane). One JVM process, per ADR-012.
 *
 * <p>Task T008.
 */
@SpringBootApplication
public class ShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortenerApplication.class, args);
    }
}
