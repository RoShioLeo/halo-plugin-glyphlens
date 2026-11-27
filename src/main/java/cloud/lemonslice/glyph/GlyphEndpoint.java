// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import java.util.Map;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public final class GlyphEndpoint implements CustomEndpoint {
    private final GlyphStore glyphs;
    private final LexiconStore lexicon;
    private final FeedbackStore feedback;
    private final SuperAdministrator administrator;
    public GlyphEndpoint(GlyphStore glyphs,LexiconStore lexicon,FeedbackStore feedback,SuperAdministrator administrator) {
        this.glyphs=glyphs;this.lexicon=lexicon;this.feedback=feedback;this.administrator=administrator;
    }
    @Override public GroupVersion groupVersion() { return GroupVersion.parseAPIVersion("console.api.glyphlens.lemonslice.cloud/v1alpha1"); }
    @Override public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("/dictionary",request -> secured(actor -> glyphs.list(request.queryParam("query").orElse(""),request.queryParam("source").orElse("all"),number(request,"page",1),number(request,"size",50))))
            .PUT("/dictionary/entries",request -> secured(actor -> request.bodyToMono(GlyphStore.Change.class).flatMap(body -> glyphs.change(body,actor))))
            .PUT("/dictionary/batch",request -> secured(actor -> request.bodyToMono(GlyphStore.BatchChange.class).flatMap(body -> glyphs.batch(body,actor))))
            .GET("/dictionary/history",request -> secured(actor -> glyphs.history()))
            .GET("/dictionary/export",request -> secured(actor -> "reviews".equals(request.queryParam("kind").orElse("")) ? glyphs.reviews() : "overrides".equals(request.queryParam("kind").orElse("")) ? glyphs.state() : glyphs.effective()))
            .GET("/sources",request -> secured(actor -> Mono.just(glyphs.source(request.queryParam("character").orElse("")))))
            .GET("/lexicon",request -> secured(actor -> lexicon.list()))
            .PUT("/lexicon",request -> secured(actor -> request.bodyToMono(LexiconStore.Request.class).flatMap(body -> lexicon.save(body,actor))))
            .GET("/feedback",request -> secured(actor -> feedback.list()))
            .PUT("/feedback/{id}",request -> secured(actor -> request.bodyToMono(FeedbackStore.Update.class).flatMap(body -> feedback.process(request.pathVariable("id"),body,actor))))
            .build();
    }
    private Mono<ServerResponse> secured(Function<String,Mono<?>> action) {
        return administrator.require().flatMap(action::apply).flatMap(value -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(value));
    }
    private int number(ServerRequest request,String key,int fallback) {
        try { return Integer.parseInt(request.queryParam(key).orElse(Integer.toString(fallback))); }
        catch(NumberFormatException failure) { throw UnicodeInput.invalid("分页参数无效"); }
    }
}
