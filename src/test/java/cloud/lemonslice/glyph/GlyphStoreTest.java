// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
    @Test void missingConfirmationDecodesAndDisableResetWorkWithStrictHostMapper() throws Exception {
        var mapper=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        var disable=mapper.readValue("{\"character\":\"甲\",\"action\":\"disable\",\"version\":null,\"note\":\"\"}",GlyphStore.Change.class);
        assertFalse(disable.confirmed());
        store.change("v2s",disable,"owner").block();
        assertEquals(List.of("甲"),store.effective("v2s").block().get("甲"));
        var disabled=store.list("v2s","甲","all",1,50).block().items().stream().filter(row->row.character().equals("甲")).findFirst().orElseThrow();
        assertEquals("disabled",disabled.source());
        assertEquals(List.of("丙"),disabled.candidates());
        var reset=mapper.readValue("{\"character\":\"甲\",\"action\":\"reset\",\"version\":0,\"confirmed\":null}",GlyphStore.Change.class);
        store.change("v2s",reset,"owner").block();
        assertEquals(List.of("丙"),store.effective("v2s").block().get("甲"));
        var approve=mapper.readValue("{\"character\":\"𤎺\",\"action\":\"approve\",\"candidates\":[\"㸇\"],\"version\":1}",GlyphStore.Change.class);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->store.change("v2s",approve,"owner").block()).getStatusCode().value());
    }
    @Test void directionalManagementAndExportsExcludeCommonRowsButConversionIncludesThem() {
        assertFalse(store.list("v2s","︽","all",1,50).block().items().stream().anyMatch(row->row.character().equals("︽")));
        assertTrue(store.list("common","︽","all",1,50).block().items().stream().anyMatch(row->row.character().equals("︽")));
        assertFalse(store.table("v2s").block().containsKey("︽"));
        assertEquals(List.of("《"),store.effective("v2s").block().get("︽"));
        store.change("common",change("乙","override",List.of("丁"),null,false),"owner").block();
        assertEquals(0,store.list("v2s","乙","changed",1,50).block().total());
        assertEquals(0,store.list("v2t","乙","changed",1,50).block().changedCount());
        assertEquals(List.of("丁"),store.effective("v2s").block().get("乙"));
    }
    @Test void commonAndDirectionalOverridesShareOneVersionAndRespectPriority() {
        assertEquals(List.of("丙"),store.effective("v2s").block().get("甲"));
        assertEquals(0L,store.change("common",change("甲","override",List.of("乙"),null,false),"owner").block().version());
        assertEquals(List.of("丙"),store.effective("v2s").block().get("甲"));assertEquals(List.of("丁"),store.effective("v2t").block().get("甲"));
        store.change("v2s",change("甲","disable",null,0L,false),"owner").block();
        assertEquals(List.of("乙"),store.effective("v2s").block().get("甲"));assertEquals(List.of("丁"),store.effective("v2t").block().get("甲"));
        assertEquals(List.of("乙"),store.history("v2s").block().getFirst().after());
        store.change("common",change("甲","override",List.of("丁"),1L,false),"owner").block();
        assertEquals(List.of("丁"),store.effective("v2s").block().get("甲"));assertEquals(List.of("丁"),store.effective("v2t").block().get("甲"));
        store.change("v2s",change("甲","reset",null,2L,false),"owner").block();
        assertEquals(List.of("丙"),store.effective("v2s").block().get("甲"));
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
    @Test void batchActionsRequireUniformStateAndRestoreSavedMappings() {
        store.change("v2s",change("甲","override",List.of("乙"),null,false),"owner").block();
        store.batch("v2s",new GlyphStore.BatchChange(List.of("甲"),"disable",0L,false),"owner").block();
        String before=fixture.resources.get("lemonslice-glyphlens-dictionaries").getData().get("payload");
        assertEquals(400,assertThrows(ResponseStatusException.class,()->store.batch("v2s",new GlyphStore.BatchChange(List.of("乾","甲"),"disable",1L,false),"owner").block()).getStatusCode().value());
        assertEquals(before,fixture.resources.get("lemonslice-glyphlens-dictionaries").getData().get("payload"));
        store.batch("v2s",new GlyphStore.BatchChange(List.of("甲"),"enable",1L,null),"owner").block();
        assertEquals(List.of("乙"),store.effective("v2s").block().get("甲"));
        assertEquals("说明",store.state("v2s").block().entries().get("甲").note());
    }
    @Test void batchReviewRequiresExplicitConfirmationAndRejectsMixedReviewStatus() {
        assertEquals(400,assertThrows(ResponseStatusException.class,()->store.batch("v2s",new GlyphStore.BatchChange(List.of("𤎺"),"approve",null,null),"owner").block()).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->store.batch("v2s",new GlyphStore.BatchChange(List.of("𤎺","甲"),"approve",null,true),"owner").block()).getStatusCode().value());
        assertTrue(fixture.resources.isEmpty());
        store.batch("v2s",new GlyphStore.BatchChange(List.of("𤎺"),"approve",null,true),"owner").block();
        assertEquals(List.of("㸇","𤓎"),store.effective("v2s").block().get("𤎺"));
        assertFalse(store.list("v2s","𤎺","all",1,50).block().items().getFirst().pending());
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
