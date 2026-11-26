package cloud.lemonslice.glyph;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BaseSupplementTest {
    private StoreFixture fixture;
    private GlyphStore store;
    @BeforeEach void setup() {
        fixture=new StoreFixture();
        var reference=new GlyphCatalog.Reference(Map.of(
            "v2s",Map.of("𠀀",List.of("甲"),"𠀁",List.of("一")),
            "v2t",Map.of("𠀀",List.of("乙"),"𠀁",List.of("一"))),Map.of());
        store=new GlyphStore(fixture.documents,new GlyphCatalog(Map.of(),Map.of(),reference));
    }
    private void change(String direction,String character,String action,List<String> candidates,Long version) {
        store.change(direction,new GlyphStore.Change(character,action,candidates,"",version,false),"owner").block();
    }
    @Test void manualMatchBecomesDurableBaseNotJustALabelAndResetRestoresIt() {
        store.change("v2s",new GlyphStore.Change("𠀀","override",List.of("甲"),"保留说明",null,false),"owner").block();
        var state=store.state("v2s").block();
        assertTrue(state.entries().isEmpty());assertEquals(List.of("甲"),state.supplements().get("𠀀").candidates());
        var row=store.list("v2s","𠀀","all",1,50).block().items().getFirst();
        assertEquals("supplement",row.source());assertEquals("保留说明",row.note());assertEquals(List.of("甲"),row.baseCandidates());
        assertEquals(0,store.list("v2s","","changed",1,50).block().total());
        change("v2s","𠀀","override",List.of("丙"),0L);
        assertEquals(List.of("丙"),store.effective("v2s").block().get("𠀀"));
        change("v2s","𠀀","reset",null,1L);
        assertEquals(List.of("甲"),store.effective("v2s").block().get("𠀀"));
        assertEquals("保留说明",store.list("v2s","𠀀","all",1,50).block().items().getFirst().note());
    }
    @Test void commonNeedsAgreementInBothDirectionsAndNonmatchingCandidatesRemainCustom() {
        change("common","𠀀","override",List.of("甲"),null);
        assertFalse(store.state("common").block().entries().isEmpty());
        change("common","𠀁","override",List.of("一"),0L);
        assertEquals(List.of("一"),store.state("common").block().supplements().get("𠀁").candidates());
        assertEquals(List.of("一"),store.effective("v2t").block().get("𠀁"));
        change("v2s","𠀀","override",List.of("甲","丙"),1L);
        assertFalse(store.state("v2s").block().entries().isEmpty());
    }
    @Test void variantSavePromotesBothTablesAndOtherWritesDoNotDropSupplements() {
        var request=new GlyphStore.VariantChange("𠀀",List.of("甲"),List.of("乙"),"",null,false);
        assertTrue(store.previewVariant(request).block().rows().stream().allMatch(GlyphStore.VariantRow::baseSupplement));
        store.saveVariant(request,"owner").block();
        change("common","𠀂","override",List.of("丙"),0L);
        assertEquals(List.of("甲"),store.state("v2s").block().supplements().get("𠀀").candidates());
        assertEquals(List.of("乙"),store.state("v2t").block().supplements().get("𠀀").candidates());
        store.batch("v2s",new GlyphStore.BatchChange(List.of("𠀀"),"disable",1L,false),"owner").block();
        assertFalse(store.effective("v2s").block().containsKey("𠀀"));
        store.batch("v2s",new GlyphStore.BatchChange(List.of("𠀀"),"enable",2L,false),"owner").block();
        assertEquals("supplement",store.list("v2s","𠀀","all",1,50).block().items().getFirst().source());
        assertEquals(List.of("甲"),store.effective("v2s").block().get("𠀀"));
    }
    @Test void existing012StateWithoutSupplementFieldStillLoads() {
        fixture.resources.put("lemonslice-glyphlens-dictionaries",StoreFixture.resource("lemonslice-glyphlens-dictionaries",0L,Map.of("payload",
            "{\"tables\":{\"v2s\":{\"entries\":{},\"history\":[],\"migrated\":false}},\"migrated\":false}")));
        assertTrue(store.state("v2s").block().supplements().isEmpty());
        change("v2s","𠀀","override",List.of("甲"),0L);
        assertEquals(List.of("甲"),store.table("v2s").block().get("𠀀"));
    }
}
