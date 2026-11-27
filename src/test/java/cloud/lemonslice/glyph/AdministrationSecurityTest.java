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
        var importer=mock(DictionaryImporter.class);
        var guard=new SuperAdministrator(mock(RoleService.class));
        var client=WebTestClient.bindToRouterFunction(new GlyphEndpoint(glyphs,lexicon,feedback,guard,importer).endpoint()).build();
        for(String path:List.of("/dictionary","/dictionary/history","/dictionary/export","/dictionary/package","/sources","/lexicon","/lexicon/candidates?character=乾","/feedback"))client.get().uri(path).exchange().expectStatus().isForbidden();
        for(String path:List.of("/dictionary/entries","/dictionary/batch","/dictionary/import","/lexicon","/feedback/id"))client.put().uri(path).bodyValue("{}").exchange().expectStatus().isForbidden();
        client.post().uri("/dictionary/import/preview").bodyValue("{}").exchange().expectStatus().isForbidden();
        for(String path:List.of("/dictionaries/v2t","/dictionaries/common","/dictionaries/variant"))client.get().uri(path).exchange().expectStatus().isNotFound();
        client.post().uri("/migration/char").exchange().expectStatus().isNotFound();
        verifyNoInteractions(glyphs,lexicon,feedback,importer);
    }
}
