// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

final class StoreFixture {
    final Map<String,ConfigMap> resources=new ConcurrentHashMap<>();
    final ReactiveExtensionClient client=mock(ReactiveExtensionClient.class);
    final VersionedDocuments documents=new VersionedDocuments(client);
    StoreFixture() {
        when(client.fetch(eq(ConfigMap.class),anyString())).thenAnswer(call -> Mono.defer(() -> Mono.justOrEmpty(resources.get(call.getArgument(1))).map(StoreFixture::copy)));
        when(client.create(any(ConfigMap.class))).thenAnswer(call -> Mono.defer(() -> {
            ConfigMap request=call.getArgument(0);
            synchronized(resources) {
                if(resources.containsKey(request.getMetadata().getName()))return Mono.error(new OptimisticLockingFailureException("created concurrently"));
                ConfigMap saved=copy(request);saved.getMetadata().setVersion(0L);resources.put(saved.getMetadata().getName(),saved);return Mono.just(copy(saved));
            }
        }));
        when(client.update(any(ConfigMap.class))).thenAnswer(call -> Mono.defer(() -> {
            ConfigMap request=call.getArgument(0);
            synchronized(resources) {
                ConfigMap current=resources.get(request.getMetadata().getName());
                if(current==null || !Objects.equals(current.getMetadata().getVersion(),request.getMetadata().getVersion()))return Mono.error(new OptimisticLockingFailureException("updated concurrently"));
                ConfigMap saved=copy(request);saved.getMetadata().setVersion(current.getMetadata().getVersion()+1);resources.put(saved.getMetadata().getName(),saved);return Mono.just(copy(saved));
            }
        }));
    }
    static ConfigMap resource(String name,Long version,Map<String,String> data) {
        var resource=new ConfigMap();var metadata=new Metadata();metadata.setName(name);metadata.setVersion(version);resource.setMetadata(metadata);resource.setData(new LinkedHashMap<>(data));return resource;
    }
    static ConfigMap copy(ConfigMap source) { return resource(source.getMetadata().getName(),source.getMetadata().getVersion(),source.getData()==null?Map.of():source.getData()); }
}
