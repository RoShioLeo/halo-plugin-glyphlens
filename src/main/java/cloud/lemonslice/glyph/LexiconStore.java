// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public final class LexiconStore {
    public record Rule(String id,String character,String candidate,List<String> contexts,String note,boolean enabled,String updatedBy,String updatedAt) {}
    public record State(Map<String,JsonNode> rules) {static State initial(){return new State(Map.of());}}
    public record Request(String id,String character,String candidate,List<String> contexts,String note,boolean enabled,Long version) {}
    public record Row(String id,String character,String candidate,List<String> contexts,String note,boolean enabled,String source) {}
    public record Page(Long version,List<Row> items,boolean wordConversionEnabled) {}
    public record PublicRule(String character,String candidate,List<String> contexts) {}
    public record Options(String character,List<String> candidates,boolean eligible) {}
    private final VersionedDocuments documents;
    private final GlyphStore glyphs;
    private final LexiconCatalog catalog;
    private final ObjectMapper mapper=new ObjectMapper();
    private static final String NAME="lemonslice-glyphlens-lexicon";
    private static final Pattern GRAPHEMES=Pattern.compile("\\X");
    public LexiconStore(VersionedDocuments documents,GlyphStore glyphs,LexiconCatalog catalog) {this.documents=documents;this.glyphs=glyphs;this.catalog=catalog;}
    private static List<String> characters(String text){return text.codePoints().mapToObj(cp->new String(Character.toChars(cp))).toList();}
    // Read old explicit-choice entries without dropping them. They are converted
    // to character/context associations only on a subsequent explicit save.
    private Map<String,Rule> saved(State state,Map<String,List<String>> mapping) {
        var result=new LinkedHashMap<String,Rule>();
        state.rules().forEach((id,value)->{
            if(value.hasNonNull("character"))result.put(id,mapper.convertValue(value,Rule.class));
            else if(value.hasNonNull("phrase")) {
                var original=characters(value.path("phrase").asText());var word=new ArrayList<>(original);
                for(int i=0;i<word.size();i++){var values=mapping.get(word.get(i));if(values!=null && values.size()==1)word.set(i,values.getFirst());}
                for(var choice:value.path("choices")){int offset=choice.path("offset").asInt(-1);if(offset>=0 && offset<word.size())word.set(offset,choice.path("target").asText());}
                for(var choice:value.path("choices")) {
                    int offset=choice.path("offset").asInt(-1);String source=choice.path("source").asText(),target=choice.path("target").asText();
                    if(offset<0 || offset>=word.size() || !original.get(offset).equals(source))continue;
                    String nextId=id+"-"+offset;
                    result.put(nextId,new Rule(nextId,source,target,List.of(String.join("",word)),value.path("sense").asText(""),value.path("enabled").asBoolean(),value.path("updatedBy").asText(""),value.path("updatedAt").asText("")));
                }
            }
        });
        return result;
    }
    private Map<String,Rule> merged(Map<String,Rule> saved) {
        var result=new LinkedHashMap<String,Rule>();
        catalog.entries().forEach((id,entry)->result.put(id,new Rule(id,entry.character(),entry.candidate(),entry.contexts(),entry.note(),true,"GlyphLens","2026-11-27")));
        result.putAll(saved);return result;
    }
    public Mono<Page> list() {
        return documents.read(NAME,State.class,State::initial).zipWith(glyphs.effective()).map(tuple->{
            var snapshot=tuple.getT1();var saved=saved(snapshot.value(),tuple.getT2());
            var rows=merged(saved).values().stream().sorted(java.util.Comparator.comparingInt((Rule r)->r.character().codePointAt(0)).thenComparing(Rule::candidate).thenComparing(Rule::id))
                .map(r->new Row(r.id(),r.character(),r.candidate(),r.contexts(),r.note(),r.enabled(),catalog.entries().containsKey(r.id())?saved.containsKey(r.id())?"modified":"default":"custom")).toList();
            return new Page(snapshot.version(),rows,false);
        });
    }
    public Mono<Options> candidates(String input) {
        String c=UnicodeInput.character(input);
        return glyphs.effective().map(mapping->{var values=mapping.getOrDefault(c,List.of(c));return new Options(c,values,values.size()>1);});
    }
    public Mono<List<PublicRule>> publicRules() {
        return documents.read(NAME,State.class,State::initial).zipWith(glyphs.effective()).map(tuple->{
            var mapping=tuple.getT2();
            return merged(saved(tuple.getT1().value(),mapping)).values().stream().filter(Rule::enabled)
                .filter(rule->{var values=mapping.getOrDefault(rule.character(),List.of());return values.size()>1 && values.contains(rule.candidate());})
                .map(rule->new PublicRule(rule.character(),rule.candidate(),rule.contexts())).toList();
        });
    }
    private static List<String> contexts(Request request,Map<String,List<String>> mapping,String target) {
        if(request.contexts()==null || request.contexts().isEmpty() || request.contexts().size()>100)throw UnicodeInput.invalid("请填写 1 至 100 个简体词条");
        var result=new java.util.LinkedHashSet<String>();
        for(String raw:request.contexts()) {
            if(raw==null)throw UnicodeInput.invalid("词条不能为空");String word=raw.trim();
            var parts=GRAPHEMES.matcher(word).results().map(m->m.group()).toList();
            if(parts.size()<2 || parts.size()>64 || parts.stream().anyMatch(c->c.codePointCount(0,c.length())!=1))throw UnicodeInput.invalid("词条须为 2 至 64 字，暂不支持组合字形");
            var normalized=new StringBuilder();
            for(String c:parts){var values=mapping.get(c);normalized.append(!c.equals(target) && values!=null && values.size()==1?values.getFirst():c);}
            String context=normalized.toString();
            if(!characters(context).contains(target))throw UnicodeInput.invalid("每个词条须包含所选候选字「"+target+"」");
            result.add(context);
        }
        return List.copyOf(result);
    }
    public Mono<GlyphStore.Saved> save(Request request,String actor) {
        String character=UnicodeInput.character(request.character()),candidate=UnicodeInput.character(request.candidate()),note=GlyphStore.note(request.note());
        return glyphs.effective().flatMap(mapping->{
            var values=mapping.getOrDefault(character,List.of());
            if(request.enabled() && (values.size()<2 || !values.contains(candidate)))return Mono.error(UnicodeInput.invalid("请选择该原字的已确认多候选字形"));
            var contexts=contexts(request,mapping,candidate);
            return documents.read(NAME,State.class,State::initial).flatMap(snapshot->{
                var rules=saved(snapshot.value(),mapping);
                String id=request.id()==null || request.id().isBlank()?UUID.randomUUID().toString():request.id();
                rules.put(id,new Rule(id,character,candidate,contexts,note,request.enabled(),actor,Instant.now().toString()));
                if(merged(rules).values().stream().mapToLong(rule->rule.contexts().size()).sum()>5000)return Mono.error(UnicodeInput.invalid("词条数量已达上限"));
                var encoded=new LinkedHashMap<String,JsonNode>();rules.forEach((key,rule)->encoded.put(key,mapper.valueToTree(rule)));
                return documents.write(snapshot,request.version(),new State(encoded)).map(GlyphStore.Saved::new);
            });
        });
    }
}
