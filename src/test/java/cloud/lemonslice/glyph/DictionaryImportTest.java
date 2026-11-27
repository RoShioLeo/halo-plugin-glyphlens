// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DictionaryImportTest {
    private static final Map<String,List<String>> BASE=Map.of("裏",List.of("里"),"乾",List.of("干","乾"));
    private final StoreFixture fixture=new StoreFixture();
    private final GlyphStore glyphs=new GlyphStore(fixture.documents,new GlyphCatalog(BASE,Map.of()));
    private final DictionaryImporter importer=new DictionaryImporter(fixture.documents,glyphs);
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private void commit(byte[] file){var preview=importer.preview(file).block();importer.commit(file,preview.stateVersion(),preview.fileHash(),true,"owner").block();}
    private static byte[] archive(String version,Map<String,List<String>> mappings,List<LexiconCatalog.Entry> lexicon) throws Exception {
        var files=new LinkedHashMap<>(new GlyphCatalog(mappings,Map.of()).files());
        var metadata=(ObjectNode)DictionaryArchive.tree(files.get("version.json"));metadata.put("version",version);
        var lexiconCounts=DictionaryArchive.JSON.valueToTree(Map.of("rules",lexicon.size(),"contexts",lexicon.stream().mapToInt(e->e.contexts().size()).sum(),"characters",lexicon.stream().map(LexiconCatalog.Entry::character).distinct().count()));
        metadata.set("lexicon",lexiconCounts);files.put("version.json",DictionaryArchive.JSON.writeValueAsBytes(metadata));
        var sources=(ObjectNode)DictionaryArchive.tree(files.get("sources.json"));((ObjectNode)sources.get("summary")).put("version",version);files.put("sources.json",DictionaryArchive.JSON.writeValueAsBytes(sources));
        files.put("lexicon.json",DictionaryArchive.JSON.writeValueAsBytes(lexicon));
        return DictionaryArchive.fromCatalog(new GlyphCatalog(files)).zip();
    }
    @Test void plainJsonNormalizesHexAndMergesWithoutTouchingOtherMappings() {
        var file=bytes("{\"U+88CF\":[\"0x91CC\"],\"9F9C\":[\"9F9F\"]}");
        var preview=importer.preview(file).block();assertEquals(1,preview.newCount());assertEquals(1,preview.unchangedCount());assertTrue(fixture.resources.isEmpty());
        commit(file);assertEquals(List.of("龟"),glyphs.effective().block().get("龜"));assertEquals(BASE.get("乾"),glyphs.effective().block().get("乾"));
        assertEquals("1.0.0",glyphs.version().block().get("version"));
        assertEquals("manual",((List<Map<String,Object>>)glyphs.source("龜").block().get("records")).getFirst().get("kind"));
    }
    @Test void snapshotExportRoundTripsDisabledCandidatesNotesAndOrder() throws Exception {
        commit(bytes("{\"format\":\"glyphlens-mappings\",\"formatVersion\":1,\"mappings\":{\"乾\":[\"乾\",\"干\"],\"裏\":[\"里\"]},\"disabled\":[\"裏\"],\"notes\":{\"裏\":\"暂时停用\",\"乾\":\"保留姓名\"}}"));
        var exported=DictionaryArchive.JSON.writeValueAsBytes(importer.exportMappings().block());
        var second=new StoreFixture();var target=new GlyphStore(second.documents,new GlyphCatalog(BASE,Map.of()));var imports=new DictionaryImporter(second.documents,target);
        var preview=imports.preview(exported).block();imports.commit(exported,preview.stateVersion(),preview.fileHash(),true,"owner").block();
        assertEquals(glyphs.effective().block(),target.effective().block());assertEquals("暂时停用",target.state().block().entries().get("裏").note());
        assertEquals(List.of("里"),target.state().block().entries().get("裏").candidates());
        assertEquals(0,imports.preview(exported).block().changes().size());
        commit(bytes("{\"裏\":[\"里\"]}"));assertEquals("暂时停用",glyphs.state().block().entries().get("裏").note());assertTrue(glyphs.effective().block().containsKey("裏"));
    }
    @Test void invalidFileIsAtomicAndRejectsDuplicateNormalizedGlyphs() {
        for(String invalid:List.of("{\"裏\":[\"里\"],\"龜\":[\"ab\"]}","{\"裏\":[\"里\"],\"88CF\":[\"里\"]}","{\"裏\":[\"里\",\"91CC\"]}","{\"裏\":[\"里\"],\"裏\":[\"裡\"]}","{\"裏\":[\"里\"]} {}","{\"format\":\"glyphlens-mappings\",\"formatVersion\":1,\"mappings\":{\"裏\":[\"里\"]},\"disabled\":[\"龜\"]}"))
            assertThrows(ResponseStatusException.class,()->importer.preview(bytes(invalid)).block(),invalid);
        assertTrue(fixture.resources.isEmpty());
    }
    @Test void requiresConfirmationSameFileAndUnchangedPreviewVersion() {
        var file=bytes("{\"龜\":[\"龟\"]}");var preview=importer.preview(file).block();
        assertThrows(ResponseStatusException.class,()->importer.commit(file,null,preview.fileHash(),false,"owner").block());
        assertThrows(ResponseStatusException.class,()->importer.commit(bytes("{\"龜\":[\"亀\"]}"),null,preview.fileHash(),true,"owner").block());
        glyphs.change(new GlyphStore.Change("裏","disable",null,"",null,false),"other").block();
        assertEquals(409,assertThrows(ResponseStatusException.class,()->importer.commit(file,preview.stateVersion(),preview.fileHash(),true,"owner").block()).getStatusCode().value());
        assertFalse(glyphs.effective().block().containsKey("龜"));
    }
    @Test void defaultUpdateKeepsManualMappingsDisabledRulesAndSiteLexicon() throws Exception {
        var lexicon=new LexiconStore(fixture.documents,glyphs,new LexiconCatalog(List.of()));
        lexicon.save(new LexiconStore.Request("keep","乾","乾",List.of("乾隆"),"站点修正",false,null),"owner").block();
        glyphs.change(new GlyphStore.Change("裏","override",List.of("裡"),"修正",null,false),"owner").block();
        glyphs.change(new GlyphStore.Change("乾","disable",null,"",0L,false),"owner").block();
        var incoming=new LinkedHashMap<>(BASE);incoming.put("龜",List.of("龟"));incoming.put("裏",List.of("里","裡"));
        var rule=new LexiconCatalog.Entry("keep","乾","干",List.of("干燥"),"默认",List.of("https://example.org/source"));
        commit(archive("1.1.0",incoming,List.of(rule)));
        assertEquals("1.1.0",glyphs.version().block().get("version"));assertEquals(List.of("裡"),glyphs.effective().block().get("裏"));assertFalse(glyphs.effective().block().containsKey("乾"));assertEquals(List.of("龟"),glyphs.effective().block().get("龜"));
        assertEquals("站点修正",lexicon.list().block().items().getFirst().note());assertTrue(lexicon.publicRules().block().isEmpty());
        var saved=glyphs.list("","all",1,50).block().version();glyphs.change(new GlyphStore.Change("裏","reset",null,"",saved,false),"owner").block();
        assertEquals(incoming.get("裏"),glyphs.effective().block().get("裏"));assertEquals("1.1.0",DictionaryArchive.read(importer.exportDefault().block()).version());
    }
    @Test void defaultVersionCannotBeReusedForChangedData() throws Exception {
        var changed=new LinkedHashMap<>(BASE);changed.put("龜",List.of("龟"));var file=archive("1.0.0",changed,List.of());
        assertThrows(ResponseStatusException.class,()->importer.preview(file).block());assertTrue(fixture.resources.isEmpty());
        commit(archive("1.0.0",BASE,List.of()));assertTrue(fixture.resources.isEmpty());
    }
    @Test void installedDefaultLexiconAndLicensesSurviveExportRoundTrip() throws Exception {
        var rule=new LexiconCatalog.Entry("dry","乾","干",List.of("干燥"),"干燥用法",List.of("https://example.org/source"));
        commit(archive("1.1.0",BASE,List.of(rule)));
        var rules=new LexiconStore(fixture.documents,glyphs,new LexiconCatalog(List.of())).publicRules().block();assertEquals("干",rules.getFirst().candidate());
        var exported=DictionaryArchive.read(importer.exportDefault().block());assertEquals("1.1.0",exported.version());assertTrue(exported.files().containsKey("LICENSE"));assertTrue(exported.files().containsKey("licenses/Unicode-LICENSE.txt"));
        assertFalse(importer.exportOverrides().block().containsKey("library"));
    }
    @Test void archiveRejectsTamperUnsafePathsAndOversizedExpansion() throws Exception {
        var good=DictionaryArchive.read(archive("1.1.0",BASE,List.of()));var files=new LinkedHashMap<>(good.files());
        files.put("dictionary.json",bytes("{\"裏\":[\"裡\"]}"));
        // Keep the original manifest while changing a payload.
        var hashes=new LinkedHashMap<String,String>();DictionaryArchive.DATA.forEach(name->hashes.put(name,DictionaryArchive.sha(good.files().get(name))));
        files.put("manifest.json",DictionaryArchive.JSON.writeValueAsBytes(Map.of("format","glyphlens-dictionary","formatVersion",1,"dictionaryVersion","1.1.0","files",hashes)));
        assertThrows(ResponseStatusException.class,()->DictionaryArchive.read(rawZip(files)));
        assertThrows(ResponseStatusException.class,()->DictionaryArchive.read(rawZip(Map.of("../dictionary.json",bytes("{}")))));
        assertThrows(ResponseStatusException.class,()->DictionaryArchive.read(rawZip(Map.of("dictionary.json",new byte[DictionaryArchive.MAX_UNPACKED+1]))));
        assertTrue(fixture.resources.isEmpty());
    }
    @Test void bundledDefaultCanBeExportedAndReadWithActualSourceMetadata() {
        var catalog=new GlyphCatalog();var archive=DictionaryArchive.fromCatalog(catalog);var restored=DictionaryArchive.read(archive.zip());
        assertEquals("1.0.0",restored.version());assertEquals(catalog.mapping(),restored.catalog().mapping());assertEquals(archive.fingerprint(),restored.fingerprint());assertEquals(12718,restored.catalog().mapping().size());
    }
    @Test void confirmedImportApprovesPendingMappingEvenWhenCandidatesMatchBaseline() {
        var pending=new GlyphCatalog.Review(List.of("干","乾"),List.of("待确认"),List.of(),List.of());
        var store=new GlyphStore(fixture.documents,new GlyphCatalog(BASE,Map.of("乾",pending)));var imports=new DictionaryImporter(fixture.documents,store);
        var file=bytes("{\"乾\":[\"干\",\"乾\"]}");var preview=imports.preview(file).block();assertEquals(1,preview.changes().size());
        imports.commit(file,preview.stateVersion(),preview.fileHash(),true,"owner").block();assertTrue(store.reviews().block().isEmpty());
    }
    @Test void archivePreservesItsOwnAttributionAndRejectsInvalidTypedIndexes() throws Exception {
        var files=new LinkedHashMap<>(DictionaryArchive.read(archive("1.1.0",BASE,List.of())).files());
        files.put("NOTICE.md",bytes("Custom dataset attribution"));var rebuilt=DictionaryArchive.fromCatalog(new GlyphCatalog(files));
        assertEquals("Custom dataset attribution",new String(DictionaryArchive.read(rebuilt.zip()).files().get("NOTICE.md"),StandardCharsets.UTF_8));
        files.put("reference.json",bytes("{\"mappings\":{},\"links\":{},\"unexpected\":true}"));
        assertThrows(ResponseStatusException.class,()->DictionaryArchive.fromCatalog(new GlyphCatalog(files)));
        assertTrue(fixture.resources.isEmpty());
    }
    private static byte[] rawZip(Map<String,byte[]> files) throws Exception {
        try(var bytes=new ByteArrayOutputStream();var zip=new ZipOutputStream(bytes)) {for(var file:files.entrySet()){zip.putNextEntry(new ZipEntry(file.getKey()));zip.write(file.getValue());zip.closeEntry();}zip.finish();return bytes.toByteArray();}
    }
}
