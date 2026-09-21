package agentic.shortener.delivery.auth;

import agentic.shortener.domain.creator.Creator;
import agentic.shortener.domain.creator.CreatorCredential;
import agentic.shortener.domain.creator.CreatorRepository;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Creator authentication. Task T052. FR-URL-018, FR-URL-019. EC-041. ADR-013.
 *
 * <p><strong>Bearer transport, for an operational reason rather than a stylistic one.</strong> Proxies,
 * log scrubbers and crash reporters redact {@code Authorization} <em>by default</em>. A custom header
 * would forfeit that protection for nothing, and the key would then turn up in the logs of every
 * intermediary nobody remembered to configure.
 *
 * <p><strong>Every denial is the same value.</strong> {@link Decision#DENIED} is a single constant
 * returned for all five reasons a request can fail: no header, empty header, wrong scheme, unknown key,
 * and a key that is known but unusable. EC-041 requires an expired credential's refusal to be
 * byte-identical to a revoked one's — and the same reasoning extends further than the edge case states:
 * if a guessed key produced a different refusal from a revoked one, an attacker could confirm which
 * guesses correspond to credentials that exist.
 *
 * <p><strong>No cryptography is written here.</strong> The hash comes from
 * {@link CreatorCredential#hashOf} and the comparison from {@link CreatorCredential#matches}, which uses
 * {@code MessageDigest.isEqual}. A filter rolling its own {@code String.equals} would reintroduce a
 * timing side channel, and that is exactly the change a later hand makes without noticing the cost.
 *
 * <p><strong>This filter never logs.</strong> It holds key material in a local variable, and FR-URL-019
 * says the application must not write key material anywhere at any level. The safest way to honour that
 * is to have no logger on the class at all, which a test asserts.
 *
 * <p><strong>The redirect path never enters this filter</strong> — T044's guard. That is enforced by
 * where it is registered ({@code AuthConfiguration}) and not by a check inside it: a filter that ran on
 * every request and opted out internally would be one edit away from not opting out.
 */
public final class CreatorAuthFilter implements Filter {

    /** Where the authenticated creator is put for the controller to read. */
    public static final String CREATOR_ID_ATTRIBUTE = "agentic.shortener.authenticatedCreatorId";

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * The outcome. {@link #DENIED} is deliberately a shared constant — see the class note on EC-041.
     * Two separately-constructed denials are equal today and can drift apart tomorrow, and that drift
     * is the disclosure CR-002 forbids.
     */
    public record Decision(boolean authenticated, Optional<UUID> creatorId) {

        /** The single denial. Identical for every reason a request can fail to authenticate. */
        public static final Decision DENIED = new Decision(false, Optional.empty());

        static Decision of(UUID creatorId) {
            return new Decision(true, Optional.of(creatorId));
        }
    }

    private final CreatorRepository creators;
    private final Clock clock;

    public CreatorAuthFilter(CreatorRepository creators, Clock clock) {
        this.creators = Objects.requireNonNull(creators, "creators");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Decides whether an {@code Authorization} header authenticates a creator.
     *
     * <p>Separated from {@link #doFilter} so the decision is testable without a servlet container, and
     * so the five denial paths can be asserted to produce one value.
     */
    public Decision authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Decision.DENIED;
        }
        // Verbatim. The stored hash covers the FULL presented string, so trimming, padding or re-casing
        // would make this a different key — and "helpfully" cleaning the value up would authenticate
        // something the operator never issued.
        String presented = authorizationHeader.substring(BEARER_PREFIX.length());
        if (presented.isEmpty()) {
            return Decision.DENIED;
        }

        Optional<CreatorCredential> found = creators.findByKeyHash(CreatorCredential.hashOf(presented));
        if (found.isEmpty()) {
            return Decision.DENIED;
        }
        CreatorCredential credential = found.get();
        if (!credential.isUsableAt(clock.instant())) {
            // Expired and revoked both land here, on the same line, returning the same value. EC-041 is
            // satisfied by there being nowhere for the two cases to diverge.
            //
            // Proved, not assumed: building a fresh Decision here instead was captured as red evidence
            // (20260921T100917Z). The two denials came out EQUAL in value, so assertEquals passed and
            // only the identity assertion failed — which is why the test asserts identity.
            return Decision.DENIED;
        }
        if (!credential.matches(presented)) {
            // Belt and braces: the lookup was by hash, so a match is implied. Asking the credential
            // anyway keeps the constant-time comparison on the authenticating path rather than only on
            // the lookup, which is where a future change to the lookup could quietly remove it.
            return Decision.DENIED;
        }

        Optional<Creator> owner = creators.findById(credential.creatorId());
        if (owner.isEmpty() || !owner.get().active()) {
            // A credential pointing at a creator that does not exist, or at a deactivated one. KE-01 has
            // no state for a request whose owner cannot be looked up.
            return Decision.DENIED;
        }
        return Decision.of(credential.creatorId());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        Decision decision = authenticate(http.getHeader("Authorization"));

        if (!decision.authenticated()) {
            deny((HttpServletResponse) response);
            return;
        }
        http.setAttribute(CREATOR_ID_ATTRIBUTE, decision.creatorId().orElseThrow());
        chain.doFilter(request, response);
    }

    /** One body for every denial, matching the contract's {@code Error} schema. */
    private static void deny(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"UNAUTHENTICATED\",\"message\":\"A valid creator credential is required.\"}");
    }
}
