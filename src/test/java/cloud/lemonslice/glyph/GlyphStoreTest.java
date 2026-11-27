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
        store=new GlyphStore(fixture.documents,new GlyphCatalog(Map.of("甲",List.of("丙"),"乾",List.of("干","乾"),"︽",List.of("《")),
            Map.of("𤎺",new GlyphCatalog.Review(List.of("㸇","𤓎"),List.of("来源冲突"),List.of(),List.of()))));
    }
    private GlyphStore.Change change(String c,String action,List<String> values,Long version,boolean confirmed) { return new GlyphStore.Change(c,action,values,"说明",version,confirmed); }
    @Test void freshInstallationUsesDefaultsAndDoesNotImportTheOldDirectionalDatabase() {
        fixture.resources.put("lemonslice-glyphlens-dictionaries",StoreFixture.resource("lemonslice-glyphlens-dictionaries",7L,Map.of("payload","{\"tables\":{\"v2t\":{}},\"migrated\":false}")));
        assertTrue(store.state().block().entries().isEmpty());assertTrue(store.history().block().isEmpty());
        assertEquals(List.of("干","乾"),store.effective().block().get("乾"));
        assertEquals(7L,fixture.resources.get("lemonslice-glyphlens-dictionaries").getMetadata().getVersion());
    }
    @Test void everyGlyphIncludingPunctuationUsesTheSameListAndExport() {
        assertEquals(List.of("《"),store.effective().block().get("︽"));
        assertEquals("︽",store.list("︽","all",1,50).block().items().getFirst().character());
        store.change(change("乙","override",List.of("丁"),null,false),"owner").block();
        assertEquals(List.of("丁"),store.effective().block().get("乙"));
        assertEquals(1,store.list("乙","changed",1,50).block().total());
        assertEquals(1,fixture.resources.size());
    }
    @Test void omittedConfirmationParsesAndDisableKeepsOriginalWhileResetRestoresDefault() throws Exception {
        var mapper=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        var disable=mapper.readValue("{\"character\":\"甲\",\"action\":\"disable\",\"version\":null}",GlyphStore.Change.class);
        assertFalse(disable.confirmed());store.change(disable,"owner").block();
        assertFalse(store.effective().block().containsKey("甲"));
        var row=store.list("甲","all",1,50).block().items().getFirst();
        assertEquals("disabled",row.source());assertEquals(List.of("丙"),row.candidates());
        store.change(change("甲","reset",null,0L,false),"owner").block();
        assertEquals(List.of("丙"),store.effective().block().get("甲"));
    }
    @Test void pendingCandidatesRequireConfirmationAndResetReturnsToPending() {
        assertFalse(store.effective().block().containsKey("𤎺"));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->store.change(change("𤎺","override",List.of("㸇"),null,false),"owner").block()).getStatusCode().value());
        assertTrue(fixture.resources.isEmpty());
        store.change(change("U+243BA","approve",List.of("U+3E07"),null,true),"owner").block();
        assertEquals(List.of("㸇"),store.effective().block().get("𤎺"));assertTrue(store.reviews().block().isEmpty());
        store.change(change("𤎺","reset",null,0L,false),"owner").block();
        assertTrue(store.reviews().block().containsKey("𤎺"));assertFalse(store.effective().block().containsKey("𤎺"));
    }
    @Test void invalidBatchIsAtomicAndConcurrentWritesDoNotOverwriteEdits() {
        store.change(change("甲","override",List.of("乙"),null,false),"owner").block();
        String before=fixture.resources.get("lemonslice-glyphlens-dictionary").getData().get("payload");
        assertEquals(400,assertThrows(ResponseStatusException.class,()->store.batch(new GlyphStore.BatchChange(List.of("甲","U+110000"),"disable",0L,false),"owner").block()).getStatusCode().value());
        assertEquals(before,fixture.resources.get("lemonslice-glyphlens-dictionary").getData().get("payload"));
        store.batch(new GlyphStore.BatchChange(List.of("甲","乾"),"disable",0L,false),"owner").block();
        assertEquals(List.of("乙"),store.state().block().entries().get("甲").candidates());assertEquals("说明",store.state().block().entries().get("甲").note());
        assertFalse(store.effective().block().containsKey("乾"));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->store.change(change("甲","override",List.of("丙"),0L,false),"other").block()).getStatusCode().value());
    }
    @Test void batchEnableRestoresSavedOrderAndMixedStatesAreRejected() {
        store.change(change("甲","override",List.of("乙","丙"),null,false),"owner").block();
        store.batch(new GlyphStore.BatchChange(List.of("甲"),"disable",0L,false),"owner").block();
        assertEquals(400,assertThrows(ResponseStatusException.class,()->store.batch(new GlyphStore.BatchChange(List.of("乾","甲"),"disable",1L,false),"owner").block()).getStatusCode().value());
        store.batch(new GlyphStore.BatchChange(List.of("甲"),"enable",1L,null),"owner").block();
        assertEquals(List.of("乙","丙"),store.effective().block().get("甲"));assertEquals("说明",store.state().block().entries().get("甲").note());
    }
    @Test void batchReviewRequiresExplicitConfirmationAndUniformPendingState() {
        assertEquals(400,assertThrows(ResponseStatusException.class,()->store.batch(new GlyphStore.BatchChange(List.of("𤎺"),"approve",null,null),"owner").block()).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->store.batch(new GlyphStore.BatchChange(List.of("𤎺","甲"),"approve",null,true),"owner").block()).getStatusCode().value());
        assertTrue(fixture.resources.isEmpty());store.batch(new GlyphStore.BatchChange(List.of("𤎺"),"approve",null,true),"owner").block();
        assertEquals(List.of("㸇","𤓎"),store.effective().block().get("𤎺"));assertFalse(store.list("𤎺","all",1,50).block().items().getFirst().pending());
    }
    @Test void rawHexAndSupplementaryGlyphsAreAcceptedWithoutReverseMappings() {
        assertEquals("一",UnicodeInput.character("0x4E00"));assertEquals("𠀀",UnicodeInput.character("20000"));assertEquals("\uf900",UnicodeInput.character("U+F900"));
        assertThrows(ResponseStatusException.class,()->UnicodeInput.character("U+D800"));assertThrows(ResponseStatusException.class,()->UnicodeInput.character("两个"));
        store.change(change("20000","override",List.of("4E00","一"),null,false),"owner").block();
        assertEquals(List.of("一"),store.effective().block().get("𠀀"));assertFalse(store.effective().block().containsKey("一"));
    }
}
