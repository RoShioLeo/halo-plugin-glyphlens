// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LexiconAndFeedbackTest {
    private GlyphStore glyphs(StoreFixture fixture){return new GlyphStore(fixture.documents,new GlyphCatalog(Map.of("乾",List.of("干","乾"),"淨",List.of("净")),Map.of()));}
    @Test void contextsChooseOnlyApprovedCandidatesAndNormaliseUnambiguousTraditionalNeighbours() {
        var fixture=new StoreFixture();var glyphs=glyphs(fixture);var lexicon=new LexiconStore(fixture.documents,glyphs,new LexiconCatalog(List.of()));
        lexicon.save(new LexiconStore.Request(null,"U+4E7E","干",List.of("干淨","干燥","干燥"),"",true,null),"owner").block();
        var rule=lexicon.publicRules().block().getFirst();assertEquals("乾",rule.character());assertEquals("干",rule.candidate());assertEquals(List.of("干净","干燥"),rule.contexts());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->lexicon.save(new LexiconStore.Request(null,"乾","字",List.of("字典"),"",true,0L),"owner").block()).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->lexicon.save(new LexiconStore.Request(null,"乾","干",List.of("乾燥"),"",true,0L),"owner").block()).getStatusCode().value());
        glyphs.change(new GlyphStore.Change("乾","disable",null,"",null,false),"owner").block();assertTrue(lexicon.publicRules().block().isEmpty());assertFalse(lexicon.list().block().wordConversionEnabled());
    }
    @Test void bundledDefaultsAreVisibleWithoutWritesAndCanBeOverriddenOrDisabled() {
        var fixture=new StoreFixture();var glyphs=glyphs(fixture);
        var defaults=new LexiconCatalog(List.of(new LexiconCatalog.Entry("default-qian","乾","乾",List.of("乾坤"),"",List.of())));
        var lexicon=new LexiconStore(fixture.documents,glyphs,defaults);
        assertEquals("default",lexicon.list().block().items().getFirst().source());assertTrue(fixture.resources.isEmpty());
        assertEquals(List.of("干","乾"),lexicon.candidates("4E7E").block().candidates());
        lexicon.save(new LexiconStore.Request("default-qian","乾","乾",List.of("乾坤"),"停用",false,null),"owner").block();
        assertTrue(lexicon.publicRules().block().isEmpty());assertEquals("modified",lexicon.list().block().items().getFirst().source());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->lexicon.save(new LexiconStore.Request("default-qian","乾","乾",List.of("乾坤"),"",true,null),"other").block()).getStatusCode().value());
    }
    @Test void oldExplicitChoiceRulesBecomeContextAssociationsWithoutSilentWrites() {
        var fixture=new StoreFixture();String payload="""
            {"rules":{"old":{"id":"old","phrase":"乾淨","choices":[{"offset":0,"source":"乾","target":"干"}],"sense":"原备注","enabled":true,"updatedBy":"owner","updatedAt":"2026-11-27"}}}
            """;
        fixture.resources.put("lemonslice-glyphlens-lexicon",StoreFixture.resource("lemonslice-glyphlens-lexicon",3L,Map.of("payload",payload)));
        var lexicon=new LexiconStore(fixture.documents,glyphs(fixture),new LexiconCatalog(List.of()));
        var row=lexicon.list().block().items().getFirst();assertEquals("乾",row.character());assertEquals("干",row.candidate());assertEquals(List.of("干净"),row.contexts());assertEquals("原备注",row.note());
        assertEquals(payload,fixture.resources.get("lemonslice-glyphlens-lexicon").getData().get("payload"));
        lexicon.save(new LexiconStore.Request(row.id(),row.character(),row.candidate(),row.contexts(),row.note(),true,3L),"owner").block();
        assertFalse(fixture.resources.get("lemonslice-glyphlens-lexicon").getData().get("payload").contains("\"phrase\""));
    }
    @Test void publicFeedbackNeverChangesTheCharacterDictionaryAndIsBounded() {
        var fixture=new StoreFixture();var feedback=new FeedbackStore(fixture.documents);var request=new FeedbackStore.Request("U+3D7E","0x3D7E","请核查字形");
        for(int i=0;i<5;i++)assertNotNull(feedback.submit(request,"visitor").block().id());
        assertEquals(429,assertThrows(ResponseStatusException.class,()->feedback.submit(request,"visitor").block()).getStatusCode().value());
        assertEquals(java.util.Set.of("lemonslice-glyphlens-feedback"),fixture.resources.keySet());assertEquals(5,feedback.list().block().items().size());
    }
}
