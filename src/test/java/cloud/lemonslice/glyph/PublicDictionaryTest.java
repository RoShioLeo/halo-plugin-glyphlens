// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
class PublicDictionaryTest {
    @Test void publicDictionaryIsUnifiedAndRemovedDirectionUrlsAreNotExposed() {
        var glyphs=mock(GlyphStore.class);var lexicon=mock(LexiconStore.class);
        when(glyphs.effective()).thenReturn(Mono.just(Map.of("乾",List.of("干","乾"),"︽",List.of("《"))));
        when(lexicon.publicRules()).thenReturn(Mono.just(List.of()));
        var routes=new PublicRoutes(glyphs,lexicon,mock(FeedbackStore.class),mock(SuperAdministrator.class));
        var client=WebTestClient.bindToRouterFunction(routes.glyphLensWeb()).build();
        client.get().uri("/tools/glyphlens/dictionary.json").exchange().expectStatus().isOk().expectBody().jsonPath("$.乾[0]").isEqualTo("干").jsonPath("$.︽[0]").isEqualTo("《");
        client.get().uri("/tools/glyphlens/lexicon.json").exchange().expectStatus().isOk().expectBody().json("[]");
        for(String path:List.of("/tools/glyphlens/dictionaries/v2t.json","/tools/glyphlens/dictionaries/v2s.json","/tools/char/char_converter/data/v2t.json"))client.get().uri(path).exchange().expectStatus().isNotFound();
        verify(glyphs,times(1)).effective();
    }
}
