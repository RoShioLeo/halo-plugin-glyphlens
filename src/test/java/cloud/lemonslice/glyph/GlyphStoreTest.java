// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GlyphStoreTest {
    private StoreFixture fixture;
    private GlyphStore store;
    @BeforeEach void setup() {
        fixture=new StoreFixture();
        var catalog=new GlyphCatalog(Map.of("common",Map.of("甲",List.of("甲"),"︽",List.of("《")),"v2s",Map.of("甲",List.of("丙"),"乾",List.of("乾","干")),"v2t",Map.of("甲",List.of("丁"))),
            Map.of("v2s",Map.of("𤎺",new GlyphCatalog.Review(List.of("㸇","𤓎"),List.of("来源冲突"),List.of(),List.of()))));
        store=new GlyphStore(fixture.documents,catalog);
    }
    private GlyphStore.Change change(String source,String action,List<String> values,Long version,boolean confirmed) { return new GlyphStore.Change(source,action,values,"说明",version,confirmed); }
    @Test void commonAndDirectionalOverridesShareOneVersionAndRespectPriority() {
        assertEquals(List.of("丙"),store.effective("v2s").block().get("甲"));
        assertEquals(0L,store.change("common",change("甲","override",List.of("乙"),null,false),"owner").block().version());
        assertEquals(List.of("乙"),store.effective("v2s").block().get("甲"));assertEquals(List.of("乙"),store.effective("v2t").block().get("甲"));
        store.change("v2s",change("甲","disable",null,0L,false),"owner").block();
        assertFalse(store.effective("v2s").block().containsKey("甲"));assertEquals(List.of("乙"),store.effective("v2t").block().get("甲"));
        store.change("common",change("甲","override",List.of("丁"),1L,false),"owner").block();
        assertFalse(store.effective("v2s").block().containsKey("甲"));assertEquals(List.of("丁"),store.effective("v2t").block().get("甲"));
        store.change("v2s",change("甲","reset",null,2L,false),"owner").block();
        assertEquals(List.of("丁"),store.effective("v2s").block().get("甲"));
        assertEquals(1,fixture.resources.size());
    }
    @Test void pendingExtrasDoNotActivateUntilExplicitlyConfirmed() {
        assertFalse(store.effective("v2s").block().containsKey("𤎺"));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->store.change("v2s",change("𤎺","override",List.of("㸇"),null,false),"owner").block()).getStatusCode().value());
        assertTrue(fixture.resources.isEmpty());
        store.change("v2s",change("U+243BA","approve",List.of("U+3E07"),null,true),"owner").block();
        assertEquals(List.of("㸇"),store.effective("v2s").block().get("𤎺"));
        assertTrue(store.reviews("v2s").block().isEmpty());
        store.change("v2s",change("𤎺","reset",null,0L,false),"owner").block();
        assertFalse(store.effective("v2s").block().containsKey("𤎺"));
    }
    @Test void batchIsAtomicAndRetainsCandidatesAndNotes() {
        store.change("v2s",change("甲","override",List.of("乙"),null,false),"owner").block();
        String before=fixture.resources.get("lemonslice-glyphlens-dictionaries").getData().get("payload");
        assertEquals(400,assertThrows(ResponseStatusException.class,()->store.disableBatch("v2s",new GlyphStore.Batch(List.of("甲","U+110000"),"",0L),"owner").block()).getStatusCode().value());
        assertEquals(before,fixture.resources.get("lemonslice-glyphlens-dictionaries").getData().get("payload"));
        store.disableBatch("v2s",new GlyphStore.Batch(List.of("甲","乾"),"",0L),"owner").block();
        assertEquals(List.of("乙"),store.state("v2s").block().entries().get("甲").candidates());
        assertEquals("说明",store.state("v2s").block().entries().get("甲").note());
        assertFalse(store.effective("v2s").block().containsKey("乾"));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->store.change("common",change("甲","override",List.of("丙"),0L,false),"owner").block()).getStatusCode().value());
    }
    @Test void migrationPreservesBothOldDictionariesAndDoesNotModifyTheirResources() {
        String payload="""
            {"entries":{"壹":{"candidates":null,"disabled":true,"note":"保持大写","updatedBy":"owner","updatedAt":"2026-11-26"},"圑":{"candidates":["团"],"disabled":false,"note":"修正","updatedBy":"owner","updatedAt":"2026-11-26"}},"history":[{"character":"圑","action":"override","before":["博"],"after":["团"],"note":"修正","updatedBy":"owner","updatedAt":"2026-11-26"}]}
            """;
        fixture.resources.put("lemonslice-char-v2s",StoreFixture.resource("lemonslice-char-v2s",25L,Map.of("dictionary",payload)));
        fixture.resources.put("lemonslice-char-v2t",StoreFixture.resource("lemonslice-char-v2t",0L,Map.of("dictionary",payload.replace("团","團"))));
        assertEquals(0L,store.migrate("owner").block().version());
        assertEquals(List.of("团"),store.effective("v2s").block().get("圑"));assertEquals(List.of("團"),store.effective("v2t").block().get("圑"));
        assertTrue(store.state("v2s").block().entries().get("壹").disabled());assertEquals("保持大写",store.state("v2s").block().entries().get("壹").note());
        assertEquals(1,store.history("v2s").block().size());
        assertEquals(payload,fixture.resources.get("lemonslice-char-v2s").getData().get("dictionary"));
        assertEquals(25L,fixture.resources.get("lemonslice-char-v2s").getMetadata().getVersion());
        assertEquals(0L,store.migrate("owner").block().version());
    }
    @Test void rawScalarCodesAndSupplementaryInputsAreSupported() {
        assertEquals("一",UnicodeInput.character("0x4E00"));assertEquals("𠀀",UnicodeInput.character("20000"));
        assertEquals("\uf900",UnicodeInput.character("U+F900"));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->UnicodeInput.character("U+D800")).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->UnicodeInput.character("两个"));
    }
}
