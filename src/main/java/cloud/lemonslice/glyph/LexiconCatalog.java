// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class LexiconCatalog {
    public record Entry(String id,String character,String candidate,List<String> contexts,String note,List<String> sources) {}
    private final Map<String,Entry> entries;
    public LexiconCatalog() { this(read()); }
    LexiconCatalog(List<Entry> values) {
        var result=new LinkedHashMap<String,Entry>();
        for(var entry:values) {
            if(result.put(entry.id(),entry)!=null)throw new IllegalStateException("默认词条标识重复："+entry.id());
        }
        entries=Map.copyOf(result);
    }
    Map<String,Entry> entries() { return entries; }
    private static List<Entry> read() {
        try(var stream=LexiconCatalog.class.getClassLoader().getResourceAsStream("data/lexicon.json")) {
            if(stream==null)throw new IllegalStateException("默认消歧词典缺失");
            return new ObjectMapper().readValue(stream,new TypeReference<>() {});
        } catch(IOException failure) {throw new IllegalStateException("无法读取默认消歧词典",failure);}
    }
}
