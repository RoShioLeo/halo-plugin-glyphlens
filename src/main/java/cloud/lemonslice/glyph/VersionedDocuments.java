// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
public final class VersionedDocuments {
    public record Snapshot<T>(ConfigMap resource, T value) {
        public Long version() { return resource.getMetadata().getVersion(); }
    }
    private final ReactiveExtensionClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    public VersionedDocuments(ReactiveExtensionClient client) { this.client = client; }
    public <T> Mono<Snapshot<T>> read(String name, Class<T> type, Supplier<T> initial) {
        return client.fetch(ConfigMap.class, name).defaultIfEmpty(empty(name)).map(resource -> {
            try {
                String payload = resource.getData() == null ? null : resource.getData().get("payload");
                return new Snapshot<>(resource, payload == null ? initial.get() : mapper.readValue(payload, type));
            } catch (Exception failure) { throw new IllegalStateException("无法读取已保存的字形资料", failure); }
        });
    }
    public <T> Mono<Long> write(Snapshot<T> snapshot, Long expected, T value) {
        if (!Objects.equals(snapshot.version(), expected)) return Mono.error(conflict());
        ConfigMap next = new ConfigMap();
        Metadata metadata = new Metadata();
        BeanUtils.copyProperties(snapshot.resource().getMetadata(), metadata);
        next.setMetadata(metadata);
        try { next.setData(new LinkedHashMap<>(Map.of("payload", mapper.writeValueAsString(value)))); }
        catch (Exception failure) { return Mono.error(failure); }
        Mono<ConfigMap> saved = expected == null ? client.create(next) : client.update(next);
        return saved.map(resource -> resource.getMetadata().getVersion()).onErrorResume(failure ->
            client.fetch(ConfigMap.class, metadata.getName()).flatMap(current ->
                !Objects.equals(current.getMetadata().getVersion(), expected) ? Mono.<Long>error(conflict()) : Mono.<Long>error(failure))
                .switchIfEmpty(Mono.error(failure)));
    }
    Mono<ConfigMap> legacy(String direction) { return client.fetch(ConfigMap.class, "lemonslice-char-" + direction); }
    private static ConfigMap empty(String name) {
        var resource = new ConfigMap();var metadata = new Metadata();metadata.setName(name);resource.setMetadata(metadata);return resource;
    }
    static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "资料已被修改，请刷新后重新确认"); }
}
