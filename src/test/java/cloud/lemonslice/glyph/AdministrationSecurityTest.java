// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import run.halo.app.core.user.service.RoleService;

class AdministrationSecurityTest {
    @Test void administratorMustHaveTheActualSuperRole() {
        var roles=mock(RoleService.class);var guard=new SuperAdministrator(roles);
        var anonymous=new AnonymousAuthenticationToken("test","anonymousUser",List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertFalse(guard.allowed().contextWrite(ReactiveSecurityContextHolder.withAuthentication(anonymous)).block());verifyNoInteractions(roles);
        when(roles.getRolesByUsername("editor")).thenReturn(Flux.just("editor-role"));
        var editor=new UsernamePasswordAuthenticationToken("editor","unused",List.of());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->guard.require().contextWrite(ReactiveSecurityContextHolder.withAuthentication(editor)).block()).getStatusCode().value());
        when(roles.getRolesByUsername("owner")).thenReturn(Flux.just("super-role"));
        var owner=new UsernamePasswordAuthenticationToken("owner","unused",List.of());
        assertEquals("owner",guard.require().contextWrite(ReactiveSecurityContextHolder.withAuthentication(owner)).block());
    }
    @Test void allPrivateEndpointsCheckPermissionsBeforeReadingOrWriting() {
        var glyphs=mock(GlyphStore.class);var lexicon=mock(LexiconStore.class);var feedback=mock(FeedbackStore.class);
        var guard=new SuperAdministrator(mock(RoleService.class));
        var client=WebTestClient.bindToRouterFunction(new GlyphEndpoint(glyphs,lexicon,feedback,guard).endpoint()).build();
        for(String path:List.of("/dictionaries/common","/dictionaries/v2s/history","/dictionaries/v2t/export","/sources","/lexicon","/feedback"))client.get().uri(path).exchange().expectStatus().isForbidden();
        client.put().uri("/dictionaries/common/entries").bodyValue("{}").exchange().expectStatus().isForbidden();
        client.put().uri("/dictionaries/v2s/batch-disable").bodyValue("{}").exchange().expectStatus().isForbidden();
        client.put().uri("/dictionaries/variant").bodyValue("{}").exchange().expectStatus().isForbidden();
        client.post().uri("/dictionaries/variant/preview").bodyValue("{}").exchange().expectStatus().isForbidden();
        client.post().uri("/migration/char").exchange().expectStatus().isForbidden();
        verifyNoInteractions(glyphs,lexicon,feedback);
    }
}
