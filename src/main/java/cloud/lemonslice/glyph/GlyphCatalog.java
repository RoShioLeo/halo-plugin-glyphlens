// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class GlyphCatalog {
    public record Review(List<String> candidates,List<String> reasons,List<String> evidence,List<String> links) {}
    public record Reference(Map<String,List<String>> mappings,Map<String,List<String>> links) {}
    private final Map<String,List<String>> mapping;
    private final Map<String,Review> reviews;
    private volatile Map<String,Object> sources;
    private volatile Reference reference;
    public GlyphCatalog() {
        var mapper=new ObjectMapper();
        mapping=read(mapper,"data/dictionary.json",new TypeReference<>() {});
        reviews=read(mapper,"data/reviews.json",new TypeReference<>() {});
    }
    GlyphCatalog(Map<String,List<String>> mapping,Map<String,Review> reviews) { this(mapping,reviews,new Reference(Map.of(),Map.of())); }
    GlyphCatalog(Map<String,List<String>> mapping,Map<String,Review> reviews,Reference reference) {
        this.mapping=mapping;this.reviews=reviews;this.reference=reference;this.sources=Map.of();
    }
    private Reference reference() {
        if(reference==null) synchronized(this) {
            if(reference==null) reference=read(new ObjectMapper(),"data/reference.json",new TypeReference<>() {});
        }
        return reference;
    }
    boolean corroborates(String character,List<String> candidates) {
        return candidates!=null && !candidates.isEmpty() && reference().mappings().getOrDefault(character,List.of()).containsAll(candidates);
    }
    List<Map<String,String>> referenceRecords(String character) {
        return reference().links().getOrDefault(character,List.of()).stream().map(url->Map.of("label","中国台湾地区教育主管部门《異體字字典》：补充参考","url",url)).toList();
    }
    Map<String,List<String>> mapping() { return mapping; }
    Map<String,Review> reviews() { return reviews; }
    Map<String,Object> sources() {
        if(sources==null) synchronized(this) {
            if(sources==null) sources=read(new ObjectMapper(),"data/sources.json",new TypeReference<>() {});
        }
        return sources;
    }
    private static <T> T read(ObjectMapper mapper,String file,TypeReference<T> type) {
        try(var stream=GlyphCatalog.class.getClassLoader().getResourceAsStream(file)) {
            if(stream==null) throw new IllegalStateException("字形资料缺失："+file);
            return mapper.readValue(stream,type);
        } catch(IOException failure) { throw new IllegalStateException("无法读取字形资料："+file,failure); }
    }
}
