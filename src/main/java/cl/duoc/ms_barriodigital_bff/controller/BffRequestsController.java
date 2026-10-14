package cl.duoc.ms_barriodigital_bff.controller;

import cl.duoc.ms_barriodigital_bff.service.RequestsClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bff/requests")
public class BffRequestsController {

    /** Claims donde Azure AD puede traer el email del usuario, en orden de preferencia. */
    private static final List<String> CLAIMS_EMAIL = List.of("preferred_username", "email", "upn");

    private final RequestsClient requestsClient;

    public BffRequestsController(RequestsClient requestsClient) {
        this.requestsClient = requestsClient;
    }

    /**
     * Un Vecino solo puede ingresar tramites a su nombre: el email se toma del token.
     * Un Funcionario puede ingresar a nombre de otro vecino (atencion presencial).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('Vecino', 'Funcionario')")
    public ResponseEntity<Object> crear(@RequestBody Map<String, Object> body,
                                        @AuthenticationPrincipal Jwt jwt,
                                        Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        Map<String, Object> request = new HashMap<>(body);
        if (roles.contains("ROLE_Vecino") && !roles.contains("ROLE_Funcionario")) {
            request.put("vecinoEmail", emailDelToken(jwt));
        }
        return requestsClient.crear(request);
    }

    /** Tramites del vecino autenticado. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('Vecino')")
    public ResponseEntity<Object> listarMios(@AuthenticationPrincipal Jwt jwt) {
        return requestsClient.listarPorVecino(emailDelToken(jwt));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Vecino', 'Funcionario', 'Admin', 'Auditor')")
    public ResponseEntity<Object> obtener(@PathVariable Long id) {
        return requestsClient.obtenerPorId(id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('Funcionario', 'Admin')")
    public ResponseEntity<Object> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return requestsClient.cambiarEstado(id, body);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('Funcionario', 'Admin', 'Auditor')")
    public ResponseEntity<Object> listar(@RequestParam(required = false) String status) {
        return requestsClient.listar(status);
    }

    private String emailDelToken(Jwt jwt) {
        return CLAIMS_EMAIL.stream()
                .map(jwt::getClaimAsString)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El token no trae un email del usuario"));
    }
}
