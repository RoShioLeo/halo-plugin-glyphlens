// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.LinkedHashMap;
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
    private final Map<String,byte[]> importedFiles;
    private final boolean fixture;
    public GlyphCatalog() {
        importedFiles=null;fixture=false;
        var mapper=new ObjectMapper();
        mapping=read(mapper,"data/dictionary.json",new TypeReference<>() {});
        reviews=read(mapper,"data/reviews.json",new TypeReference<>() {});
    }
    GlyphCatalog(Map<String,List<String>> mapping,Map<String,Review> reviews) { this(mapping,reviews,new Reference(Map.of(),Map.of())); }
    GlyphCatalog(Map<String,List<String>> mapping,Map<String,Review> reviews,Reference reference) {
        importedFiles=null;fixture=true;
        this.mapping=mapping;this.reviews=reviews;this.reference=reference;this.sources=Map.of();
    }
    GlyphCatalog(Map<String,byte[]> files) {
        importedFiles=Map.copyOf(files);fixture=false;
        var mapper=new ObjectMapper();
        mapping=read(mapper,"data/dictionary.json",new TypeReference<>() {});
        reviews=read(mapper,"data/reviews.json",new TypeReference<>() {});
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
        return reference().mappings().getOrDefault(character,List.of()).stream().flatMap(candidate->reference().links().getOrDefault(character,List.of()).stream()
            .map(url->Map.of("candidate",candidate,"label","中国台湾地区教育主管部门《異體字字典》：补充参考（需核对原条）","url",url))).toList();
    }
    Map<String,List<String>> mapping() { return mapping; }
    Map<String,Review> reviews() { return reviews; }
    String version() { return fixture?"1.0.0":String.valueOf(metadata().get("version")); }
    Map<String,Object> metadata() {
        return fixture?Map.of("version","1.0.0","counts",DictionaryArchive.counts(mapping,reviews.size()),"lexicon",Map.of("rules",0,"contexts",0,"characters",0)):
            read(new ObjectMapper(),"data/version.json",new TypeReference<>() {});
    }
    List<LexiconCatalog.Entry> lexicon() { return fixture?List.of():read(new ObjectMapper(),"data/lexicon.json",new TypeReference<>() {}); }
    Map<String,byte[]> files() {
        if(importedFiles!=null)return importedFiles;
        var result=new LinkedHashMap<String,byte[]>();var mapper=new ObjectMapper();
        if(fixture) {
            try {
                result.put("dictionary.json",mapper.writeValueAsBytes(mapping));result.put("reviews.json",mapper.writeValueAsBytes(reviews));
                result.put("reference.json",mapper.writeValueAsBytes(reference()));result.put("lexicon.json",mapper.writeValueAsBytes(List.of()));
                result.put("version.json",mapper.writeValueAsBytes(metadata()));
                result.put("sources.json",mapper.writeValueAsBytes(Map.of("summary",Map.of("version",version(),"counts",DictionaryArchive.counts(mapping,reviews.size())),"entries",Map.of(),"links",Map.of())));
            }catch(IOException failure){throw new IllegalStateException(failure);}
        } else for(String name:DictionaryArchive.DATA)try(var stream=GlyphCatalog.class.getClassLoader().getResourceAsStream("data/"+name)) {
            if(stream==null)throw new IllegalStateException("字形资料缺失："+name);result.put(name,stream.readAllBytes());
        }catch(IOException failure){throw new IllegalStateException(failure);}
        return Map.copyOf(result);
    }
    Map<String,Object> sources() {
        if(sources==null) synchronized(this) {
            if(sources==null) sources=read(new ObjectMapper(),"data/sources.json",new TypeReference<>() {});
        }
        return sources;
    }
    private <T> T read(ObjectMapper mapper,String file,TypeReference<T> type) {
        if(importedFiles!=null)try {
            var bytes=importedFiles.get(file.substring("data/".length()));
            if(bytes==null)throw new IllegalStateException("字库文件缺失："+file);return mapper.readValue(bytes,type);
        }catch(IOException failure){throw new IllegalStateException("无法读取导入字库："+file,failure);}
        try(var stream=GlyphCatalog.class.getClassLoader().getResourceAsStream(file)) {
            if(stream==null) throw new IllegalStateException("字形资料缺失："+file);
            return mapper.readValue(stream,type);
        } catch(IOException failure) { throw new IllegalStateException("无法读取字形资料："+file,failure); }
    }
}
