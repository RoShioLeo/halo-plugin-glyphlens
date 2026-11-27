// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
class BaseSupplementTest {
    private StoreFixture fixture;private GlyphStore store;
    @BeforeEach void setup() {
        fixture=new StoreFixture();
        var reference=new GlyphCatalog.Reference(Map.of("𠀀",List.of("甲")),Map.of());
        store=new GlyphStore(fixture.documents,new GlyphCatalog(Map.of(),Map.of(),reference));
    }
    private void change(String c,String action,List<String> values,Long version) {store.change(new GlyphStore.Change(c,action,values,"保留说明",version,false),"owner").block();}
    @Test void matchingReferenceBecomesDurableBaseAndResetRestoresIt() {
        change("𠀀","override",List.of("甲"),null);
        assertTrue(store.state().block().entries().isEmpty());assertEquals(List.of("甲"),store.state().block().supplements().get("𠀀").candidates());
        assertEquals("supplement",store.list("𠀀","all",1,50).block().items().getFirst().source());
        change("𠀀","override",List.of("丙"),0L);assertEquals(List.of("丙"),store.effective().block().get("𠀀"));
        change("𠀀","reset",null,1L);assertEquals(List.of("甲"),store.effective().block().get("𠀀"));
        assertEquals("保留说明",store.list("𠀀","all",1,50).block().items().getFirst().note());
    }
    @Test void nonmatchingCandidatesRemainCustomAndOtherWritesPreserveSupplements() {
        change("𠀀","override",List.of("甲"),null);change("𠀁","override",List.of("乙"),0L);
        assertEquals(List.of("甲"),store.state().block().supplements().get("𠀀").candidates());
        assertEquals(List.of("乙"),store.state().block().entries().get("𠀁").candidates());
    }
    @Test void disableAndEnableRetainSupplementCandidatesAndNote() {
        change("𠀀","override",List.of("甲"),null);
        store.batch(new GlyphStore.BatchChange(List.of("𠀀"),"disable",0L,false),"owner").block();assertFalse(store.effective().block().containsKey("𠀀"));
        store.batch(new GlyphStore.BatchChange(List.of("𠀀"),"enable",1L,false),"owner").block();
        assertEquals("supplement",store.list("𠀀","all",1,50).block().items().getFirst().source());assertEquals("保留说明",store.state().block().supplements().get("𠀀").note());
    }
}
