// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public final class LexiconStore {
    public record Choice(int offset, String source, String target) {}
    public record Rule(String id, String phrase, List<Choice> choices, String sense, boolean enabled, String updatedBy, String updatedAt) {}
    public record State(Map<String,Rule> rules) { static State initial() { return new State(Map.of()); } }
    public record Request(String id, String phrase, String result, String sense, boolean enabled, Long version) {}
    public record Page(Long version, List<Rule> items, boolean wordConversionEnabled) {}
    public record PublicRule(String phrase, List<Choice> choices, String sense) {}
    private final VersionedDocuments documents;
    private final GlyphStore glyphs;
    private static final String NAME = "lemonslice-glyphlens-lexicon";
    public LexiconStore(VersionedDocuments documents, GlyphStore glyphs) { this.documents = documents; this.glyphs = glyphs; }
    public Mono<Page> list() { return documents.read(NAME,State.class,State::initial).map(snapshot -> new Page(snapshot.version(),new ArrayList<>(snapshot.value().rules().values()),false)); }
    public Mono<List<PublicRule>> publicRules() {
        return documents.read(NAME,State.class,State::initial).zipWith(glyphs.effective()).map(tuple -> {
            var mapping = tuple.getT2();
            return tuple.getT1().value().rules().values().stream().filter(Rule::enabled)
                .filter(rule -> rule.choices().stream().allMatch(choice -> mapping.getOrDefault(choice.source(),List.of(choice.source())).size()>1 && mapping.getOrDefault(choice.source(),List.of(choice.source())).contains(choice.target())))
                .map(rule -> new PublicRule(rule.phrase(),rule.choices(),rule.sense())).toList();
        });
    }
    public Mono<GlyphStore.Saved> save(Request request, String actor) {
        String phrase = request.phrase() == null ? "" : request.phrase().trim();
        String result = request.result() == null ? "" : request.result().trim();
        List<String> input = phrase.codePoints().mapToObj(point -> new String(Character.toChars(point))).toList();
        List<String> output = result.codePoints().mapToObj(point -> new String(Character.toChars(point))).toList();
        if (input.size()<2 || input.size()>64 || output.size()!=input.size()) throw UnicodeInput.invalid("词条须为 2 至 64 字，结果必须与原文等长；当前仅支持候选消歧");
        String sense = GlyphStore.note(request.sense());
        return glyphs.effective().flatMap(mapping -> {
            List<Choice> choices = new ArrayList<>();
            for(int i=0;i<input.size();i++) {
                List<String> candidates = mapping.getOrDefault(input.get(i),List.of(input.get(i)));
                if(!candidates.contains(output.get(i))) return Mono.error(UnicodeInput.invalid("结果只能选择已确认字形候选，请先核准字形关系"));
                if(candidates.size()>1) choices.add(new Choice(i,input.get(i),output.get(i)));
            }
            if(choices.isEmpty()) return Mono.error(UnicodeInput.invalid("该词条没有需要消歧的多候选字形"));
            return documents.read(NAME,State.class,State::initial).flatMap(snapshot -> {
                Map<String,Rule> rules = new LinkedHashMap<>(snapshot.value().rules());
                String id = request.id()==null || request.id().isBlank() ? UUID.randomUUID().toString() : request.id();
                if(!rules.containsKey(id) && rules.size()>=5000) throw UnicodeInput.invalid("词条数量已达上限");
                rules.put(id,new Rule(id,phrase,choices,sense,request.enabled(),actor,Instant.now().toString()));
                return documents.write(snapshot,request.version(),new State(rules)).map(GlyphStore.Saved::new);
            });
        });
    }
}
