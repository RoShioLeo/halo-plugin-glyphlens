// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
public final class FeedbackStore {
    public record Request(String character, String candidate, String direction, String note) {}
    public record Ticket(String id, String character, String candidate, String direction, String note, String status, String createdAt, String reply, String updatedBy) {}
    public record State(Map<String,Ticket> tickets) { static State initial() { return new State(Map.of()); } }
    public record Update(String status, String reply, Long version) {}
    public record Page(Long version,List<Ticket> items) {}
    public record Receipt(String id) {}
    private final VersionedDocuments documents;
    private final Map<String,List<Long>> recent = new LinkedHashMap<>();
    private static final String NAME="lemonslice-glyphlens-feedback";
    public FeedbackStore(VersionedDocuments documents) { this.documents=documents; }
    public Mono<Receipt> submit(Request request, String visitor) {
        GlyphCatalog.validate(request.direction());
        String source=UnicodeInput.character(request.character());
        String candidate=request.candidate()==null || request.candidate().isBlank() ? "" : UnicodeInput.character(request.candidate());
        String note=GlyphStore.note(request.note());
        if(note.isBlank() && candidate.isBlank()) throw UnicodeInput.invalid("请填写建议字形或说明");
        synchronized(recent) {
            long now=System.currentTimeMillis();
            recent.values().forEach(values -> values.removeIf(at -> now-at>600_000));
            recent.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            if(recent.size()>10_000) recent.clear();
            var values=recent.computeIfAbsent(visitor,key -> new ArrayList<>());
            if(values.size()>=5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"反馈较频繁，请稍后再试");
            values.add(now);
        }
        String id=UUID.randomUUID().toString();
        return Mono.defer(() -> documents.read(NAME,State.class,State::initial).flatMap(snapshot -> {
            var tickets=new LinkedHashMap<>(snapshot.value().tickets());
            if(tickets.size()>=2000) return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"反馈暂满，请稍后提交"));
            tickets.put(id,new Ticket(id,source,candidate,request.direction(),note,"pending",Instant.now().toString(),"",""));
            return documents.write(snapshot,snapshot.version(),new State(tickets)).map(version -> new Receipt(id));
        })).retryWhen(Retry.max(2).filter(failure -> failure instanceof ResponseStatusException status && status.getStatusCode().value()==409));
    }
    public Mono<Page> list() { return documents.read(NAME,State.class,State::initial).map(snapshot -> new Page(snapshot.version(),snapshot.value().tickets().values().stream().toList().reversed())); }
    public Mono<GlyphStore.Saved> process(String id,Update update,String actor) {
        if(!Set.of("pending","resolved","rejected").contains(update.status())) throw UnicodeInput.invalid("反馈状态无效");
        String reply=GlyphStore.note(update.reply());
        return documents.read(NAME,State.class,State::initial).flatMap(snapshot -> {
            Ticket previous=snapshot.value().tickets().get(id);
            if(previous==null) return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,"反馈不存在"));
            var tickets=new LinkedHashMap<>(snapshot.value().tickets());
            tickets.put(id,new Ticket(id,previous.character(),previous.candidate(),previous.direction(),previous.note(),update.status(),previous.createdAt(),reply,actor));
            return documents.write(snapshot,update.version(),new State(tickets)).map(GlyphStore.Saved::new);
        });
    }
}
