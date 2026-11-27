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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public final class GlyphStore {
    public record Entry(List<String> candidates,boolean disabled,String note,String updatedBy,String updatedAt,boolean approved) {}
    public record Audit(String character,String action,List<String> before,List<String> after,String note,String updatedBy,String updatedAt) {}
    public record State(Map<String,Entry> entries,List<Audit> history,Map<String,Entry> supplements) {
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
    public GlyphStore(VersionedDocuments documents,GlyphCatalog catalog) { this.documents=documents;this.catalog=catalog; }
    private Mono<VersionedDocuments.Snapshot<State>> snapshot() {
        return documents.read("lemonslice-glyphlens-dictionary",State.class,State::initial);
    }
    private Map<String,List<String>> base(State state) {
        var result=new LinkedHashMap<>(catalog.mapping());
        state.supplements().forEach((c,entry)->result.put(c,entry.candidates()));
        return result;
    }
    private Map<String,List<String>> table(State state) {
        var result=base(state);
        state.entries().forEach((c,entry)->{
            if(entry.disabled()) result.remove(c);
            else if(entry.candidates()!=null && (!catalog.reviews().containsKey(c) || entry.approved())) result.put(c,entry.candidates());
        });
        return result;
    }
    public Mono<Map<String,List<String>>> effective() { return snapshot().map(s->table(s.value())); }
    public Mono<State> state() { return snapshot().map(VersionedDocuments.Snapshot::value); }
    public Mono<Page> list(String query,String source,int page,int size) {
        if(page<1 || size<1 || size>100) throw UnicodeInput.invalid("分页参数无效");
        if(!Set.of("all","changed","pending").contains(source)) throw UnicodeInput.invalid("范围无效");
        String term=UnicodeInput.decode(query);
        return snapshot().map(snapshot->{
            State state=snapshot.value();var effective=table(state);var baseline=base(state);
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
                var after=table(new State(entries,List.of(),supplements)).getOrDefault(c,List.of(c));
                history.add(new Audit(c,change.action(),before.getOrDefault(c,List.of(c)),after,note,actor,now));
            }
            return documents.write(snapshot,changes.getFirst().version(),new State(entries,history,supplements)).map(Saved::new);
        });
    }
    public Mono<Map<String,GlyphCatalog.Review>> reviews() {
        return snapshot().map(snapshot->{
            var result=new LinkedHashMap<String,GlyphCatalog.Review>();
            catalog.reviews().forEach((c,review)->{var entry=snapshot.value().known(c);if(entry==null || !entry.approved() && !entry.disabled()) result.put(c,review);});
            return result;
        });
    }
    public Mono<List<Audit>> history() { return state().map(state->state.history().reversed().stream().limit(500).toList()); }
    @SuppressWarnings("unchecked")
    public Map<String,Object> source(String query) {
        String c=UnicodeInput.decode(query);
        if(c.isBlank()) return Map.of("summary",catalog.sources().getOrDefault("summary",Map.of()),"links",catalog.sources().getOrDefault("links",Map.of()));
        var index=(Map<String,Object>)catalog.sources().getOrDefault("entries",Map.of());
        return Map.of("character",c,"records",index.getOrDefault(c,catalog.referenceRecords(c)));
    }
    static String note(String text) {
        String value=text==null?"":text.trim();if(value.length()>500) throw UnicodeInput.invalid("说明最多 500 字");return value;
    }
}
