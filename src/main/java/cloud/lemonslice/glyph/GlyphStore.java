// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.Base64;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public final class GlyphStore {
    public record Entry(List<String> candidates,boolean disabled,String note,String updatedBy,String updatedAt,boolean approved) {}
    public record Audit(String character,String action,List<String> before,List<String> after,String note,String updatedBy,String updatedAt) {}
    public record Library(String version,String fingerprint,String archive) {}
    public record State(Map<String,Entry> entries,List<Audit> history,Map<String,Entry> supplements,Library library) {
        public State(Map<String,Entry> entries,List<Audit> history,Map<String,Entry> supplements) { this(entries,history,supplements,null); }
        public State { supplements=supplements==null?Map.of():supplements; }
        static State initial() { return new State(Map.of(),List.of(),Map.of()); }
        Entry known(String character) { return entries.getOrDefault(character,supplements.get(character)); }
    }
    public record Change(String character,String action,List<String> candidates,String note,Long version,Boolean confirmed) {
        public Change { confirmed=Boolean.TRUE.equals(confirmed); }
    }
    public record BatchChange(List<String> characters,String action,Long version,Boolean confirmed) {}
    public record Saved(Long version) {}
    public record Row(String character,List<String> candidates,List<String> baseCandidates,String source,String note,
                      boolean pending,boolean reviewRequired,List<String> savedCandidates,List<String> reviewCandidates,List<String> reviewReasons,List<String> reviewLinks) {}
    public record Page(List<Row> items,int total,int page,int size,Long version,int effectiveCount,int changedCount,int pendingCount) {}
    private final VersionedDocuments documents;
    private final GlyphCatalog catalog;
    private record Cached(String fingerprint,GlyphCatalog catalog) {}
    private volatile Cached cached;
    public GlyphStore(VersionedDocuments documents,GlyphCatalog catalog) { this.documents=documents;this.catalog=catalog; }
    Mono<VersionedDocuments.Snapshot<State>> snapshot() {
        return documents.read("lemonslice-glyphlens-dictionary",State.class,State::initial);
    }
    GlyphCatalog selected(State state) {
        if(state.library()==null)return catalog;
        var current=cached;
        if(current!=null && current.fingerprint().equals(state.library().fingerprint()))return current.catalog();
        var archive=DictionaryArchive.read(Base64.getDecoder().decode(state.library().archive()));
        if(!archive.version().equals(state.library().version()) || !archive.fingerprint().equals(state.library().fingerprint()))throw new IllegalStateException("已安装字库的版本或校验值不一致");
        var loaded=archive.catalog();cached=new Cached(archive.fingerprint(),loaded);return loaded;
    }
    Map<String,List<String>> base(State state) {
        var result=new LinkedHashMap<>(selected(state).mapping());
        state.supplements().forEach((c,entry)->result.put(c,entry.candidates()));
        return result;
    }
    Map<String,List<String>> table(State state) {
        var catalog=selected(state);
        var result=base(state);
        state.entries().forEach((c,entry)->{
            if(entry.disabled()) result.remove(c);
            else if(entry.candidates()!=null && (!catalog.reviews().containsKey(c) || entry.approved())) result.put(c,entry.candidates());
        });
        return result;
    }
    public Mono<Map<String,List<String>>> effective() { return snapshot().map(s->table(s.value())); }
    public Mono<State> state() { return snapshot().map(VersionedDocuments.Snapshot::value); }
    public Mono<Map<String,Object>> version() { return state().map(state->selected(state).metadata()); }
    Mono<Optional<List<LexiconCatalog.Entry>>> importedLexicon() { return state().map(state->state.library()==null?Optional.empty():Optional.of(selected(state).lexicon())); }
    public Mono<Page> list(String query,String source,int page,int size) {
        if(page<1 || size<1 || size>100) throw UnicodeInput.invalid("分页参数无效");
        if(!Set.of("all","changed","pending").contains(source)) throw UnicodeInput.invalid("范围无效");
        String term=UnicodeInput.decode(query);
        return snapshot().map(snapshot->{
            State state=snapshot.value();var effective=table(state);var baseline=base(state);
            var catalog=selected(state);
            var keys=new LinkedHashSet<>(baseline.keySet());keys.addAll(catalog.reviews().keySet());keys.addAll(state.entries().keySet());
            var rows=new ArrayList<Row>();int pendingCount=0;
            for(String c:keys) {
                Entry local=state.entries().get(c),entry=state.known(c);
                var review=catalog.reviews().get(c);
                boolean pending=review!=null && (entry==null || !entry.approved() && !entry.disabled());
                if(pending) pendingCount++;
                if(source.equals("changed") && local==null || source.equals("pending") && !pending) continue;
                var candidates=entry!=null && entry.disabled() ? entry.candidates()!=null?entry.candidates():baseline.getOrDefault(c,List.of(c)) : effective.getOrDefault(c,List.of(c));
                String haystack=c+String.join("",candidates)+(review==null?"":String.join("",review.candidates()));
                if(!term.isEmpty() && !haystack.contains(term) && !UnicodeInput.code(c).contains(term.toUpperCase())) continue;
                rows.add(new Row(c,candidates,baseline.getOrDefault(c,List.of(c)),entry!=null && entry.disabled()?"disabled":local!=null?"custom":state.supplements().containsKey(c)?"supplement":baseline.containsKey(c)?"base":"pending",
                    entry==null?"":entry.note(),pending,review!=null && (entry==null || !entry.approved() || entry.disabled()),entry==null?null:entry.candidates(),
                    review==null?List.of():review.candidates(),review==null?List.of():review.reasons(),review==null?List.of():review.links()));
            }
            rows.sort(Comparator.comparingInt(row->row.character().codePointAt(0)));
            int start=Math.min(rows.size(),(int)Math.min(Integer.MAX_VALUE,((long)page-1)*size));
            return new Page(rows.subList(start,Math.min(rows.size(),start+size)),rows.size(),page,size,snapshot.version(),effective.size(),state.entries().size(),pendingCount);
        });
    }
    public Mono<Saved> change(Change request,String actor) {
        String c=UnicodeInput.character(request.character()),action=request.action();
        if(!Set.of("override","approve","disable","enable","reset").contains(action==null?"":action)) throw UnicodeInput.invalid("操作无效");
        var values=request.candidates()==null?List.<String>of():request.candidates().stream().map(UnicodeInput::character).distinct().toList();
        if(Set.of("override","approve").contains(action) && (values.isEmpty() || values.size()>50)) throw UnicodeInput.invalid("请输入 1 至 50 个单字候选");
        if(action.equals("approve") && !request.confirmed()) throw UnicodeInput.invalid("请先明确确认该字形关系");
        return changes(List.of(new Change(c,action,values,note(request.note()),request.version(),request.confirmed())),actor);
    }
    public Mono<Saved> batch(BatchChange request,String actor) {
        if(request.characters()==null || request.characters().isEmpty() || request.characters().size()>1000 || !Set.of("disable","enable","approve").contains(request.action()==null?"":request.action())) throw UnicodeInput.invalid("批量操作参数无效");
        if(request.action().equals("approve") && !Boolean.TRUE.equals(request.confirmed())) throw UnicodeInput.invalid("请先确认所选映射及候选顺序");
        return snapshot().flatMap(snapshot->{
            if(!java.util.Objects.equals(snapshot.version(),request.version())) return Mono.error(VersionedDocuments.conflict());
            var catalog=selected(snapshot.value());
            var changes=new ArrayList<Change>();
            for(String input:new LinkedHashSet<>(request.characters())) {
                String c=UnicodeInput.character(input);var entry=snapshot.value().known(c);var review=catalog.reviews().get(c);
                boolean disabled=entry!=null && entry.disabled(),pending=review!=null && !disabled && (entry==null || !entry.approved());
                if(!(disabled?"enable":pending?"approve":"disable").equals(request.action())) return Mono.error(UnicodeInput.invalid("所选映射状态不一致，请刷新后重新选择"));
                if(entry==null && review==null && !base(snapshot.value()).containsKey(c)) return Mono.error(UnicodeInput.invalid("所选映射不在当前字库中"));
                changes.add(new Change(c,request.action(),request.action().equals("approve")?review.candidates():List.of(),entry==null?"":entry.note(),request.version(),request.confirmed()));
            }
            return changes(changes,actor);
        });
    }
    private Mono<Saved> changes(List<Change> changes,String actor) {
        return snapshot().flatMap(snapshot->{
            if(!java.util.Objects.equals(snapshot.version(),changes.getFirst().version())) return Mono.error(VersionedDocuments.conflict());
            State previous=snapshot.value();var entries=new LinkedHashMap<>(previous.entries());var supplements=new LinkedHashMap<>(previous.supplements());var history=new ArrayList<>(previous.history());
            var catalog=selected(previous);
            var before=table(previous);String now=Instant.now().toString();
            for(Change change:changes) {
                String c=change.character(),note=change.note();var old=entries.getOrDefault(c,supplements.get(c));
                if(catalog.reviews().containsKey(c) && Set.of("override","approve").contains(change.action()) && !change.confirmed() && (old==null || !old.approved() || old.disabled())) return Mono.error(UnicodeInput.invalid("该关系仍待确认，请审阅后使用确认并保存"));
                if(change.action().equals("reset")) entries.remove(c);
                else if(change.action().equals("enable")) {
                    if(old==null || !old.disabled()) return Mono.error(UnicodeInput.invalid("该映射未停用，请刷新后重试"));
                    var supplement=supplements.get(c);
                    if(supplement!=null && java.util.Objects.equals(old.candidates(),supplement.candidates()) && java.util.Objects.equals(old.note(),supplement.note())) entries.remove(c);
                    else entries.put(c,new Entry(old.candidates(),false,old.note(),actor,now,old.approved()));
                } else if(change.action().equals("disable")) {
                    if(note.isBlank() && old!=null) note=old.note();
                    entries.put(c,new Entry(old==null?before.get(c):old.candidates(),true,note,actor,now,old!=null && old.approved()));
                } else {
                    var entry=new Entry(change.candidates(),false,note,actor,now,true);
                    if(catalog.corroborates(c,change.candidates())) { supplements.put(c,entry);entries.remove(c); }
                    else entries.put(c,entry);
                }
                var after=table(new State(entries,List.of(),supplements,previous.library())).getOrDefault(c,List.of(c));
                history.add(new Audit(c,change.action(),before.getOrDefault(c,List.of(c)),after,note,actor,now));
            }
            return documents.write(snapshot,changes.getFirst().version(),new State(entries,history,supplements,previous.library())).map(Saved::new);
        });
    }
    public Mono<Map<String,GlyphCatalog.Review>> reviews() {
        return snapshot().map(snapshot->{
            var result=new LinkedHashMap<String,GlyphCatalog.Review>();
            var catalog=selected(snapshot.value());
            catalog.reviews().forEach((c,review)->{var entry=snapshot.value().known(c);if(entry==null || !entry.approved() && !entry.disabled()) result.put(c,review);});
            return result;
        });
    }
    public Mono<List<Audit>> history() { return state().map(state->state.history().reversed().stream().limit(500).toList()); }
    @SuppressWarnings("unchecked")
    public Mono<Map<String,Object>> source(String query) {
        String c=UnicodeInput.decode(query);
        return state().map(state->{
            var catalog=selected(state);
            if(c.isBlank()) {
                var summary=new LinkedHashMap<>((Map<String,Object>)catalog.sources().getOrDefault("summary",Map.of()));
                summary.put("version",catalog.version());summary.put("counts",DictionaryArchive.counts(catalog.mapping(),catalog.reviews().size()));
                return Map.of("summary",summary,"links",catalog.sources().getOrDefault("links",Map.of()));
            }
            var index=(Map<String,Object>)catalog.sources().getOrDefault("entries",Map.of());
            var entry=state.known(c);var candidates=entry!=null && entry.candidates()!=null?entry.candidates():base(state).getOrDefault(c,List.of(c));
            var records=new ArrayList<Map<String,Object>>();
            for(var record:(List<Map<String,Object>>)index.getOrDefault(c,catalog.referenceRecords(c)))if(!"retained".equals(record.get("kind")) && !c.equals(record.get("candidate")) && (!record.containsKey("candidate") || candidates.contains(record.get("candidate"))))records.add(record);
            if(entry!=null)for(String target:candidates)if(!target.equals(c) && records.stream().noneMatch(record->target.equals(record.get("candidate"))))records.add(Map.of("candidate",target,"kind","manual","label","手动维护来源","steps",List.of()));
            return Map.of("character",c,"records",records);
        });
    }
    static String note(String text) {
        String value=text==null?"":text.trim();if(value.length()>500) throw UnicodeInput.invalid("说明最多 500 字");return value;
    }
}
