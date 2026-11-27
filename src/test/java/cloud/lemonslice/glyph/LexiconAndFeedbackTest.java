// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LexiconAndFeedbackTest {
    @Test void dictionaryCanOnlySelectApprovedCandidatesAndCannotTranslateWords() {
        var fixture=new StoreFixture();var catalog=new GlyphCatalog(Map.of("乾",List.of("干","乾")),Map.of());
        var glyphs=new GlyphStore(fixture.documents,catalog);var lexicon=new LexiconStore(fixture.documents,glyphs);
        lexicon.save(new LexiconStore.Request(null,"乾燥","干燥","不湿",true,null),"owner").block();
        var rule=lexicon.publicRules().block().getFirst();assertEquals(0,rule.choices().getFirst().offset());assertEquals("干",rule.choices().getFirst().target());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->lexicon.save(new LexiconStore.Request(null,"鼠标","滑鼠","",true,0L),"owner").block()).getStatusCode().value());
        glyphs.change(new GlyphStore.Change("乾","disable",null,"",null,false),"owner").block();
        assertTrue(lexicon.publicRules().block().isEmpty());
        assertFalse(lexicon.list().block().wordConversionEnabled());
    }
    @Test void publicFeedbackNeverChangesTheCharacterDictionaryAndIsBounded() {
        var fixture=new StoreFixture();var feedback=new FeedbackStore(fixture.documents);
        var request=new FeedbackStore.Request("U+3D7E","0x3D7E","请核查字形");
        for(int i=0;i<5;i++) assertNotNull(feedback.submit(request,"visitor").block().id());
        assertEquals(429,assertThrows(ResponseStatusException.class,()->feedback.submit(request,"visitor").block()).getStatusCode().value());
        assertEquals(java.util.Set.of("lemonslice-glyphlens-feedback"),fixture.resources.keySet());
        assertEquals(5,feedback.list().block().items().size());
    }
}
