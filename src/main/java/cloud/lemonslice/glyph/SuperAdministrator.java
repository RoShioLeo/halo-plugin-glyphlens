// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.user.service.RoleService;

@Component
public final class SuperAdministrator {
    private final RoleService roles;
    public SuperAdministrator(RoleService roles) { this.roles = roles; }
    public Mono<String> require() {
        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication)
            .filter(authentication -> authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken))
            .flatMap(authentication -> roles.getRolesByUsername(authentication.getName()).hasElement("super-role")
                .filter(Boolean::booleanValue).map(allowed -> authentication.getName()))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可以管理字形资料")));
    }
    public Mono<Boolean> allowed() {
        return require().map(actor -> true).onErrorResume(ResponseStatusException.class, failure ->
            failure.getStatusCode().value() == 403 ? Mono.just(false) : Mono.error(failure));
    }
}
