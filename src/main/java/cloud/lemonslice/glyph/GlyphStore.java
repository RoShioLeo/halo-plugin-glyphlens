// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    public record Entry(List<String> candidates, boolean disabled, String note, String updatedBy, String updatedAt, boolean approved) {}
    public record Audit(String character, String action, List<String> before, List<String> after, String note, String updatedBy, String updatedAt) {}
    public record State(Map<String, Entry> entries, List<Audit> history, boolean migrated) {
        static State initial() { return new State(Map.of(), List.of(), false); }
    }
    public record Database(Map<String, State> tables, boolean migrated) {
        static Database initial() { return new Database(Map.of(), false); }
    }
    private record View(VersionedDocuments.Snapshot<Database> root, State value, State common) {
        Long version() { return root.version(); }
    }
    public record Change(String character, String action, List<String> candidates, String note, Long version, Boolean confirmed) {
        public Change { confirmed = Boolean.TRUE.equals(confirmed); }
    }
    public record Batch(List<String> characters, String note, Long version) {}
    public record BatchChange(List<String> characters, String action, Long version, Boolean confirmed) {}
    public record Saved(Long version) {}
    public record Row(String character, List<String> candidates, List<String> baseCandidates, String source, String scope, String note,
                      boolean pending, boolean reviewRequired, List<String> savedCandidates, List<String> reviewCandidates, List<String> reviewReasons, List<String> reviewLinks) {}
    public record Page(List<Row> items, int total, int page, int size, Long version, int effectiveCount, int changedCount, int pendingCount) {}
    private final VersionedDocuments documents;
    private final GlyphCatalog catalog;
    public GlyphStore(VersionedDocuments documents, GlyphCatalog catalog) { this.documents = documents; this.catalog = catalog; }
    private Mono<VersionedDocuments.Snapshot<Database>> database() { return documents.read("lemonslice-glyphlens-dictionaries", Database.class, Database::initial); }
    private Mono<View> snapshot(String direction) {
        GlyphCatalog.validate(direction);
        return database().map(root -> new View(root,root.value().tables().getOrDefault(direction,State.initial()),root.value().tables().getOrDefault("common",State.initial())));
    }
    public Mono<Map<String, List<String>>> effective(String direction) { return snapshot(direction).map(state -> merged(direction, state.value(),state.common())); }
    private Map<String,List<String>> table(String direction, State state) {
        var result = new LinkedHashMap<>(catalog.mapping(direction));
        overlay(direction,result,state);
        return result;
    }
    public Mono<Map<String,List<String>>> table(String direction) { return snapshot(direction).map(view -> table(direction,view.value())); }
    private Map<String, List<String>> merged(String direction, State state, State common) {
        if (direction.equals("common")) return table(direction,state);
        Map<String,List<String>> result = table("common",common);
        result.putAll(table(direction,state));
        return result;
    }
    private void overlay(String direction, Map<String,List<String>> result,State state) {
        state.entries().forEach((character, entry) -> {
            if (entry.disabled()) result.remove(character);
            else if (entry.candidates() != null && (!catalog.reviews(direction).containsKey(character) || entry.approved())) result.put(character, entry.candidates());
        });
    }
    public Mono<Page> list(String direction, String query, String source, int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw UnicodeInput.invalid("分页参数无效");
        if (!Set.of("all", "changed", "pending").contains(source)) throw UnicodeInput.invalid("范围无效");
        String term = UnicodeInput.decode(query);
        return snapshot(direction).map(snapshot -> {
            State state = snapshot.value();Map<String,List<String>> effective = table(direction,state);
            Map<String,List<String>> baseValues = catalog.mapping(direction);
            Set<String> keys = new LinkedHashSet<>(baseValues.keySet());
            keys.addAll(catalog.reviews(direction).keySet());keys.addAll(state.entries().keySet());
            List<Row> rows = new ArrayList<>();int pendingCount = 0;
            for (String character : keys) {
                Entry local = state.entries().get(character);
                Entry entry = local;
                var review = catalog.reviews(direction).get(character);
                boolean pending = review != null && (entry == null || !entry.approved()) && (entry == null || !entry.disabled());
                if (pending) pendingCount++;
                if (source.equals("changed") && entry == null || source.equals("pending") && !pending) continue;
                List<String> candidates = entry != null && entry.disabled()
                    ? entry.candidates() != null ? entry.candidates() : baseValues.getOrDefault(character,List.of(character))
                    : effective.getOrDefault(character, List.of(character));
                String haystack = character + String.join("", candidates) + (review == null ? "" : String.join("", review.candidates()));
                if (!term.isEmpty() && !haystack.contains(term) && !UnicodeInput.code(character).contains(term.toUpperCase())) continue;
                rows.add(new Row(character, candidates, baseValues.getOrDefault(character, List.of(character)),
                    entry != null && entry.disabled() ? "disabled" : local != null ? "custom" : baseValues.containsKey(character) ? "base" : "pending",
                    direction,
                    entry == null ? "" : entry.note(), pending, review != null && (entry == null || !entry.approved() || entry.disabled()), entry == null ? null : entry.candidates(), review == null ? List.of() : review.candidates(),
                    review == null ? List.of() : review.reasons(), review == null ? List.of() : review.links()));
            }
            rows.sort(Comparator.comparingInt(row -> row.character().codePointAt(0)));
            int start = Math.min(rows.size(), (int)Math.min(Integer.MAX_VALUE, ((long)page - 1) * size));
            Set<String> changed = new LinkedHashSet<>(state.entries().keySet());
            int activeCount = (int) keys.stream().filter(effective::containsKey).count();
            return new Page(rows.subList(start, Math.min(rows.size(), start + size)), rows.size(), page, size, snapshot.version(), activeCount, changed.size(), pendingCount);
        });
    }
    public Mono<Saved> change(String direction, Change request, String actor) {
        var character = UnicodeInput.character(request.character());
        String action = request.action();
        if (!Set.of("override", "approve", "disable", "enable", "reset").contains(action == null ? "" : action)) throw UnicodeInput.invalid("操作无效");
        List<String> candidates = request.candidates() == null ? List.of() : request.candidates().stream().map(UnicodeInput::character).distinct().toList();
        if (Set.of("override", "approve").contains(action) && (candidates.isEmpty() || candidates.size() > 50)) throw UnicodeInput.invalid("请输入 1 至 50 个单字候选");
        if (action.equals("approve") && !request.confirmed()) throw UnicodeInput.invalid("请先明确确认该字形关系");
        return changes(direction, List.of(new Change(character, action, candidates, note(request.note()), request.version(), request.confirmed())), actor);
    }
    public Mono<Saved> disableBatch(String direction, Batch request, String actor) {
        return batch(direction,new BatchChange(request.characters(),"disable",request.version(),false),actor);
    }
    public Mono<Saved> batch(String direction, BatchChange request, String actor) {
        if (request.characters() == null || request.characters().isEmpty() || request.characters().size() > 1000
            || !Set.of("disable","enable","approve").contains(request.action() == null ? "" : request.action())) throw UnicodeInput.invalid("批量操作参数无效");
        if (request.action().equals("approve") && !Boolean.TRUE.equals(request.confirmed())) throw UnicodeInput.invalid("请先确认所选映射及候选顺序");
        return snapshot(direction).flatMap(view -> {
            if (!java.util.Objects.equals(view.version(),request.version())) return Mono.error(VersionedDocuments.conflict());
            var changes = new ArrayList<Change>();
            for (String input : new LinkedHashSet<>(request.characters())) {
                String character = UnicodeInput.character(input);
                var entry = view.value().entries().get(character);
                var review = catalog.reviews(direction).get(character);
                boolean disabled = entry != null && entry.disabled();
                boolean pending = review != null && !disabled && (entry == null || !entry.approved());
                String expected = disabled ? "enable" : pending ? "approve" : "disable";
                if (!expected.equals(request.action())) return Mono.error(UnicodeInput.invalid("所选映射状态不一致，请刷新后重新选择"));
                if (entry == null && review == null && !catalog.mapping(direction).containsKey(character)) return Mono.error(UnicodeInput.invalid("所选映射不在当前字库中"));
                changes.add(new Change(character,request.action(),request.action().equals("approve") ? review.candidates() : List.of(),
                    entry == null ? "" : entry.note(),request.version(),Boolean.TRUE.equals(request.confirmed())));
            }
            return changes(direction,changes,actor);
        });
    }
    private Mono<Saved> changes(String direction, List<Change> changes, String actor) {
        return snapshot(direction).flatMap(snapshot -> {
            if (!java.util.Objects.equals(snapshot.version(), changes.getFirst().version())) return Mono.error(VersionedDocuments.conflict());
            State previous = snapshot.value();var entries = new LinkedHashMap<>(previous.entries());var history = new ArrayList<>(previous.history());
            var before = merged(direction, previous,snapshot.common());String now = Instant.now().toString();
            for (Change change : changes) {
                Entry old = entries.get(change.character());String note = change.note();
                if (catalog.reviews(direction).containsKey(change.character()) && Set.of("override", "approve").contains(change.action())
                    && !change.confirmed() && (old == null || !old.approved() || old.disabled())) {
                    return Mono.error(UnicodeInput.invalid("该关系仍待确认，请审阅后使用确认并保存"));
                }
                if (change.action().equals("reset")) entries.remove(change.character());
                else if (change.action().equals("enable")) {
                    if (old == null || !old.disabled()) return Mono.error(UnicodeInput.invalid("该映射未停用，请刷新后重试"));
                    entries.put(change.character(),new Entry(old.candidates(),false,old.note(),actor,now,old.approved()));
                }
                else if (change.action().equals("disable")) {
                    if (note.isBlank() && old != null) note = old.note();
                    entries.put(change.character(), new Entry(old == null ? before.get(change.character()) : old.candidates(), true, note, actor, now, old != null && old.approved()));
                } else entries.put(change.character(), new Entry(change.candidates(), false, note, actor, now, true));
                List<String> after = merged(direction,new State(entries,List.of(),previous.migrated()),snapshot.common())
                    .getOrDefault(change.character(), List.of(change.character()));
                history.add(new Audit(change.character(), change.action(), before.getOrDefault(change.character(), List.of(change.character())), after, note, actor, now));
            }
            var tables = new LinkedHashMap<>(snapshot.root().value().tables());
            tables.put(direction,new State(entries,history,previous.migrated()));
            return documents.write(snapshot.root(), changes.getFirst().version(), new Database(tables,snapshot.root().value().migrated())).map(Saved::new);
        });
    }
    public Mono<State> state(String direction) { return snapshot(direction).map(View::value); }
    public Mono<List<Audit>> history(String direction) { return state(direction).map(state -> state.history().reversed().stream().limit(500).toList()); }
    public Map<String,Object> sources() { return catalog.sources(); }
    @SuppressWarnings("unchecked")
    public Map<String,Object> source(String direction,String query) {
        GlyphCatalog.validate(direction);String character=UnicodeInput.decode(query);
        if(character.isBlank()) return Map.of("summary",catalog.sources().getOrDefault("summary",Map.of()),"links",catalog.sources().getOrDefault("links",Map.of()));
        Map<String,Object> index=(Map<String,Object>)catalog.sources().getOrDefault("entries",Map.of());
        Map<String,Object> common=(Map<String,Object>)index.getOrDefault("common",Map.of());
        Map<String,Object> local=(Map<String,Object>)index.getOrDefault(direction,Map.of());
        return Map.of("character",character,"records",local.getOrDefault(character,common.getOrDefault(character,List.of())));
    }
    public Mono<Map<String,GlyphCatalog.Review>> reviews(String direction) {
        return snapshot(direction).map(snapshot -> {
            Map<String,GlyphCatalog.Review> result=new LinkedHashMap<>();
            catalog.reviews(direction).forEach((character,review) -> {
                Entry entry=snapshot.value().entries().get(character);
                if(entry==null && !direction.equals("common")) entry=snapshot.common().entries().get(character);
                if(entry==null || !entry.approved() && !entry.disabled()) result.put(character,review);
            });
            return result;
        });
    }
    public Mono<Saved> migrate(String actor) {
        return database().flatMap(snapshot -> {
            if (snapshot.value().migrated()) return Mono.just(new Saved(snapshot.version()));
            if (snapshot.value().tables().values().stream().anyMatch(table -> !table.entries().isEmpty())) return Mono.error(VersionedDocuments.conflict());
            return documents.legacy("v2s").defaultIfEmpty(new run.halo.app.extension.ConfigMap())
                .zipWith(documents.legacy("v2t").defaultIfEmpty(new run.halo.app.extension.ConfigMap()))
                .flatMap(legacy -> {
                    var tables = new LinkedHashMap<>(snapshot.value().tables());
                    tables.put("v2s",legacyTable(legacy.getT1(),actor));tables.put("v2t",legacyTable(legacy.getT2(),actor));
                    return documents.write(snapshot,snapshot.version(),new Database(tables,true)).map(Saved::new);
                });
        });
    }
    private State legacyTable(run.halo.app.extension.ConfigMap legacy,String actor) {
        if(legacy.getData()==null || !legacy.getData().containsKey("dictionary")) return State.initial();
        try {
            var tree = new ObjectMapper().readTree(legacy.getData().get("dictionary"));
            Map<String,Entry> entries = new LinkedHashMap<>();
            tree.path("entries").fields().forEachRemaining(field -> {
                var value=field.getValue();List<String> candidates=new ArrayList<>();
                value.path("candidates").forEach(candidate -> candidates.add(UnicodeInput.character(candidate.asText())));
                entries.put(UnicodeInput.character(field.getKey()),new Entry(candidates.isEmpty()?null:candidates,value.path("disabled").asBoolean(),value.path("note").asText(""),value.path("updatedBy").asText(actor),value.path("updatedAt").asText(""),true));
            });
            List<Audit> history=new ArrayList<>();
            tree.path("history").forEach(value -> {
                List<String> before=new ArrayList<>(),after=new ArrayList<>();value.path("before").forEach(item -> before.add(item.asText()));value.path("after").forEach(item -> after.add(item.asText()));
                history.add(new Audit(value.path("character").asText(),value.path("action").asText(),before,after,value.path("note").asText(""),value.path("updatedBy").asText(actor),value.path("updatedAt").asText("")));
            });
            return new State(entries,history,true);
        } catch(Exception failure) { throw new IllegalStateException("旧字库迁移未完成，原数据未修改",failure); }
    }
    static String note(String text) {
        String value = text == null ? "" : text.trim();if (value.length() > 500) throw UnicodeInput.invalid("说明最多 500 字");return value;
    }
}
