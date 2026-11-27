// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlyphSourcesTest {
    @Test @SuppressWarnings("unchecked")
    void sourcesKeepDocumentaryEvidenceSeparateFromConfirmationWithoutWritingSiteState() {
        var fixture = new StoreFixture();
        var store = new GlyphStore(fixture.documents, new GlyphCatalog());
        var result = store.source("U+3437").block();
        assertEquals("㐷", result.get("character"));
        var records = (List<Map<String,Object>>)result.get("records");
        assertTrue(records.stream().anyMatch(record -> "骂".equals(record.get("candidate")) && "documented".equals(record.get("kind"))));
        assertFalse(records.toString().contains("maintainer-decisions"));
        var confirmation = (Map<String,Object>)result.get("confirmation");
        assertEquals("confirmed", confirmation.get("status"));
        assertEquals(List.of("骂"), confirmation.get("candidates"));
        var added = (Map<String,Object>)store.source("竃").block().get("confirmation");
        assertEquals("维护者手工补充并确认", added.get("label"));
        assertTrue(((Map<?,?>)store.source("龘").block().get("confirmation")).isEmpty());
        assertTrue(fixture.resources.isEmpty());
    }
    @Test @SuppressWarnings("unchecked")
    void manualCandidateDoesNotInheritReferenceForAnotherTarget() {
        var fixture=new StoreFixture();var reference=new GlyphCatalog.Reference(Map.of("甲",List.of("乙")),Map.of("甲",List.of("https://example.org/yi")));
        var store=new GlyphStore(fixture.documents,new GlyphCatalog(Map.of(),Map.of(),reference));
        store.change(new GlyphStore.Change("甲","override",List.of("丙"),"",null,false),"owner").block();
        var records=(List<Map<String,Object>>)store.source("甲").block().get("records");
        assertEquals(1,records.size());assertEquals("丙",records.getFirst().get("candidate"));assertEquals("manual",records.getFirst().get("kind"));assertFalse(records.getFirst().containsKey("url"));
    }
}
