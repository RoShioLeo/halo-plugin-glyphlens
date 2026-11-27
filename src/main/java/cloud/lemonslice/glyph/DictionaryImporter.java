// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public final class DictionaryImporter {
    public record Change(String character,List<String> before,List<String> incoming,List<String> after,String action,boolean conflict) {}
    public record Preview(String type,String currentDictionaryVersion,String incomingDictionaryVersion,Long stateVersion,String fileHash,
                          int inputCount,int newCount,int changedCount,int disabledCount,int unchangedCount,int manualKept,List<Change> changes) {}
    private record Upload(DictionaryArchive archive,Map<String,List<String>> mappings,Set<String> disabled,Map<String,String> notes,String baseVersion) {}
    private record Plan(GlyphStore.State state,Preview preview,boolean changed) {}
    private final VersionedDocuments documents;
    private final GlyphStore glyphs;
    public DictionaryImporter(VersionedDocuments documents,GlyphStore glyphs){this.documents=documents;this.glyphs=glyphs;}

    public Mono<Preview> preview(byte[] file) {
        var upload=parse(file);
        return glyphs.snapshot().map(snapshot->plan(upload,file,snapshot,"",false).preview());
    }
    public Mono<GlyphStore.Saved> commit(byte[] file,Long expectedVersion,String fileHash,boolean confirmed,String actor) {
        if(!confirmed)throw UnicodeInput.invalid("请先核对导入预览并确认字形关系及候选顺序");
        if(!DictionaryArchive.sha(file).equals(fileHash))throw UnicodeInput.invalid("导入文件已变化，请重新预览");
        var upload=parse(file);
        return glyphs.snapshot().flatMap(snapshot->{
            if(!Objects.equals(snapshot.version(),expectedVersion))return Mono.error(VersionedDocuments.conflict());
            var plan=plan(upload,file,snapshot,actor,true);
            if(!plan.changed())return Mono.just(new GlyphStore.Saved(snapshot.version()));
            return documents.write(snapshot,expectedVersion,plan.state()).map(GlyphStore.Saved::new);
        });
    }
    public Mono<Map<String,Object>> exportMappings() {
        return glyphs.state().map(state->{
            var mappings=new LinkedHashMap<>(glyphs.table(state));var disabled=new ArrayList<String>();var notes=new LinkedHashMap<String,String>();
            state.entries().forEach((c,entry)->{
                if(entry.disabled()){disabled.add(c);mappings.put(c,entry.candidates()==null?glyphs.base(state).getOrDefault(c,List.of(c)):entry.candidates());}
                if(entry.note()!=null && !entry.note().isBlank())notes.put(c,entry.note());
            });
            state.supplements().forEach((c,entry)->{if(!notes.containsKey(c) && entry.note()!=null && !entry.note().isBlank())notes.put(c,entry.note());});
            var ordered=new LinkedHashMap<String,List<String>>();mappings.keySet().stream().sorted(Comparator.comparingInt(c->c.codePointAt(0))).forEach(c->ordered.put(c,mappings.get(c)));
            disabled.sort(Comparator.comparingInt(c->c.codePointAt(0)));
            return Map.of("format","glyphlens-mappings","formatVersion",1,"baseDictionaryVersion",glyphs.selected(state).version(),"mappings",ordered,"disabled",disabled,"notes",notes);
        });
    }
    public Mono<byte[]> exportDefault() {return glyphs.state().map(state->DictionaryArchive.fromCatalog(glyphs.selected(state)).zip());}
    public Mono<Map<String,Object>> exportOverrides() {return glyphs.state().map(state->Map.of("entries",state.entries(),"supplements",state.supplements(),"history",state.history()));}

    private Upload parse(byte[] file) {
        if(file.length>DictionaryArchive.MAX_UPLOAD)throw UnicodeInput.invalid("导入文件不能超过 8 MiB");
        if(file.length>1 && file[0]=='P' && file[1]=='K')return new Upload(DictionaryArchive.read(file),Map.of(),Set.of(),Map.of(),"");
        var root=DictionaryArchive.tree(file);
        boolean envelope=root.has("format");
        if(!envelope)return new Upload(null,DictionaryArchive.mappings(root,true),Set.of(),Map.of(),"");
        if(!"glyphlens-mappings".equals(root.path("format").asText()) || !root.path("formatVersion").isIntegralNumber() || root.path("formatVersion").asInt()!=1)throw UnicodeInput.invalid("不支持的映射文件格式版本");
        var allowed=Set.of("format","formatVersion","baseDictionaryVersion","mappings","disabled","notes");
        root.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw UnicodeInput.invalid("映射文件包含未知字段："+key);});
        var mappings=DictionaryArchive.mappings(root.path("mappings"),true);var disabled=new LinkedHashSet<String>();var notes=new LinkedHashMap<String,String>();
        if(root.has("disabled")) {
            if(!root.get("disabled").isArray() || root.get("disabled").size()>50000)throw UnicodeInput.invalid("disabled 须为最多 50000 个原字的数组");
            for(var value:root.get("disabled")){if(!value.isTextual())throw UnicodeInput.invalid("停用原字格式无效");String c=UnicodeInput.character(value.asText());if(!disabled.add(c))throw UnicodeInput.invalid("停用原字重复："+c);}
        }
        if(root.has("notes")) {
            if(!root.get("notes").isObject())throw UnicodeInput.invalid("notes 须为逐字备注对象");
            root.get("notes").fields().forEachRemaining(field->{
                String c=UnicodeInput.character(field.getKey());
                if(!field.getValue().isTextual() || !mappings.containsKey(c) && !disabled.contains(c))throw UnicodeInput.invalid("备注须对应本文件中的原字："+c);
                if(notes.put(c,GlyphStore.note(field.getValue().asText()))!=null)throw UnicodeInput.invalid("归一后备注原字重复："+c);
            });
        }
        String version=root.path("baseDictionaryVersion").asText("");
        if(root.has("baseDictionaryVersion") && (!root.get("baseDictionaryVersion").isTextual() || version.length()>32 || !version.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")))throw UnicodeInput.invalid("基线版本须为 1.0.0 形式");
        return new Upload(null,mappings,Set.copyOf(disabled),Map.copyOf(notes),version);
    }
    private Plan plan(Upload upload,byte[] file,VersionedDocuments.Snapshot<GlyphStore.State> snapshot,String actor,boolean audit) {
        var previous=snapshot.value();var current=glyphs.selected(previous);var before=glyphs.table(previous);var changes=new ArrayList<Change>();
        String now=Instant.now().toString();var history=new ArrayList<>(previous.history());
        GlyphStore.State next;String type,targetVersion;int inputCount,comparedCount,manualKept=0,newCount=0,disabledCount=0;boolean changed;
        if(upload.archive()!=null) {
            type="default";targetVersion=upload.archive().version();var incoming=upload.archive().catalog();
            var existing=DictionaryArchive.fromCatalog(current);
            if(current.version().equals(targetVersion) && !existing.fingerprint().equals(upload.archive().fingerprint()))throw UnicodeInput.invalid("相同字库版本对应不同数据，请先递增字库版本");
            changed=!existing.fingerprint().equals(upload.archive().fingerprint());
            var library=new GlyphStore.Library(targetVersion,upload.archive().fingerprint(),Base64.getEncoder().encodeToString(file));
            next=new GlyphStore.State(previous.entries(),history,previous.supplements(),changed?library:previous.library());
            var after=glyphs.table(next);var keys=new LinkedHashSet<>(current.mapping().keySet());keys.addAll(incoming.mapping().keySet());
            comparedCount=keys.size();
            var manual=new LinkedHashSet<>(previous.entries().keySet());manual.addAll(previous.supplements().keySet());manualKept=manual.size();inputCount=incoming.mapping().size();
            for(String c:keys) {
                var oldDefault=current.mapping().getOrDefault(c,List.of(c));var newDefault=incoming.mapping().getOrDefault(c,List.of(c));
                if(oldDefault.equals(newDefault) && current.mapping().containsKey(c)==incoming.mapping().containsKey(c))continue;
                boolean conflict=manual.contains(c);String action=conflict?"保留手动修正":!current.mapping().containsKey(c)?"新增默认":!incoming.mapping().containsKey(c)?"移除默认":"更新默认";
                if(!current.mapping().containsKey(c))newCount++;if(!incoming.mapping().containsKey(c))disabledCount++;
                changes.add(new Change(c,before.getOrDefault(c,List.of(c)),newDefault,after.getOrDefault(c,List.of(c)),action,conflict));
            }
            if(audit && changed)history.add(new GlyphStore.Audit("默认字库","导入默认字库",List.of(current.version()),List.of(targetVersion),"保留站点手动修正",actor,now));
        } else {
            type="mappings";targetVersion=upload.baseVersion().isEmpty()?current.version():upload.baseVersion();
            var entries=new LinkedHashMap<>(previous.entries());var keys=new LinkedHashSet<>(upload.mappings().keySet());keys.addAll(upload.disabled());inputCount=keys.size();comparedCount=keys.size();
            var baseline=glyphs.base(previous);
            for(String c:keys) {
                var old=previous.known(c);var values=upload.mappings().get(c);
                if(values==null)values=old!=null && old.candidates()!=null?old.candidates():baseline.get(c);
                if(values==null)throw UnicodeInput.invalid("停用项不在当前字库或导入映射中："+c);
                boolean disabled=upload.disabled().contains(c),wasDisabled=old!=null && old.disabled();
                String note=upload.notes().containsKey(c)?upload.notes().get(c):old==null?"":old.note();
                var supplement=previous.supplements().get(c);
                String baselineNote=supplement==null?"":supplement.note();
                if(!disabled && values.equals(baseline.get(c)) && note.equals(baselineNote) && !current.reviews().containsKey(c))entries.remove(c);
                else entries.put(c,new GlyphStore.Entry(values,disabled,note,actor,now,true));
                var newEntry=entries.getOrDefault(c,supplement);
                boolean same=Objects.equals(old==null?baseline.get(c):old.candidates(),newEntry==null?baseline.get(c):newEntry.candidates())
                    && wasDisabled==disabled && Objects.equals(old==null?"":old.note(),newEntry==null?"":newEntry.note())
                    && (!current.reviews().containsKey(c) || (old!=null && old.approved())==(newEntry!=null && newEntry.approved()));
                if(same){if(previous.entries().containsKey(c))entries.put(c,previous.entries().get(c));else entries.remove(c);continue;}
                var after=disabled?List.of(c):values;
                String action=disabled && !wasDisabled?"停用":wasDisabled && !disabled?"启用":!before.containsKey(c) && !disabled?"新增":"更新";
                if(action.equals("新增"))newCount++;if(action.equals("停用"))disabledCount++;
                changes.add(new Change(c,before.getOrDefault(c,List.of(c)),values,after,action,previous.entries().containsKey(c) || previous.supplements().containsKey(c)));
                if(audit)history.add(new GlyphStore.Audit(c,"导入",before.getOrDefault(c,List.of(c)),after,note,actor,now));
            }
            changed=!changes.isEmpty();next=new GlyphStore.State(entries,history,previous.supplements(),previous.library());
        }
        changes.sort(Comparator.comparingInt(row->row.character().codePointAt(0)));
        int adjusted=Math.max(0,changes.size()-newCount-disabledCount),unchanged=Math.max(0,comparedCount-changes.size());
        var preview=new Preview(type,current.version(),targetVersion,snapshot.version(),DictionaryArchive.sha(file),inputCount,newCount,adjusted,disabledCount,unchanged,manualKept,List.copyOf(changes));
        return new Plan(next,preview,changed);
    }
}
