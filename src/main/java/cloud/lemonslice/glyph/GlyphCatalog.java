// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class GlyphCatalog {
    public record Review(List<String> candidates, List<String> reasons, List<String> evidence, List<String> links) {}
    private final Map<String, Map<String, List<String>>> mappings;
    private final Map<String, Map<String, Review>> reviews;
    private volatile Map<String, Object> sources;

    public GlyphCatalog() {
        var mapper = new ObjectMapper();
        this.mappings = Map.of("common", read(mapper, "data/common.json", new TypeReference<>() {}), "v2s", read(mapper, "data/v2s.json", new TypeReference<>() {}),
            "v2t", read(mapper, "data/v2t.json", new TypeReference<>() {}));
        this.reviews = read(mapper, "data/reviews.json", new TypeReference<>() {});
    }
    GlyphCatalog(Map<String, Map<String, List<String>>> mappings, Map<String, Map<String, Review>> reviews) {
        this.mappings = mappings; this.reviews = reviews; this.sources = Map.of();
    }
    Map<String, List<String>> mapping(String direction) { validate(direction); return mappings.getOrDefault(direction, Map.of()); }
    Map<String, Review> reviews(String direction) { validate(direction); return reviews.getOrDefault(direction, Map.of()); }
    Map<String, Object> sources() {
        if(sources==null) synchronized(this) {
            if(sources==null) sources=read(new ObjectMapper(),"data/sources.json",new TypeReference<>() {});
        }
        return sources;
    }
    static void validate(String direction) {
        if (!List.of("common", "v2s", "v2t").contains(direction)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "字库不存在");
    }
    private static <T> T read(ObjectMapper mapper, String file, TypeReference<T> type) {
        try (var stream = GlyphCatalog.class.getClassLoader().getResourceAsStream(file)) {
            if (stream == null) throw new IllegalStateException("字形资料缺失：" + file);
            return mapper.readValue(stream, type);
        } catch (IOException failure) { throw new IllegalStateException("无法读取字形资料：" + file, failure); }
    }
}
