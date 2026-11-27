// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public final class PublicRoutes {
    private static final Set<String> FILES=Set.of("index.html","styles.css","app.js","engine.js","lexicon.js","review-document.js","review-view.js","appearance.js","img/lemon.svg","img/glyphlens.svg","img/glyphlens-dark.svg","LICENSE","NOTICE.md");
    private final GlyphStore glyphs;
    private final LexiconStore lexicon;
    private final FeedbackStore feedback;
    private final SuperAdministrator administrator;
    public PublicRoutes(GlyphStore glyphs,LexiconStore lexicon,FeedbackStore feedback,SuperAdministrator administrator) {
        this.glyphs=glyphs;this.lexicon=lexicon;this.feedback=feedback;this.administrator=administrator;
    }
    @Bean public RouterFunction<ServerResponse> glyphLensWeb() {
        return RouterFunctions.route()
            .GET("/tools/glyphlens",request -> file("index.html"))
            .GET("/tools/glyphlens/",request -> file("index.html"))
            .GET("/tools/glyphlens/access",request -> administrator.allowed().flatMap(allowed -> json(Map.of("canManage",allowed))))
            .GET("/tools/glyphlens/dictionary.json",request -> glyphs.effective().flatMap(PublicRoutes::json))
            .GET("/tools/glyphlens/lexicon.json",request -> lexicon.publicRules().flatMap(PublicRoutes::json))
            .GET("/tools/glyphlens/feedback-token",this::csrf)
            .POST("/tools/glyphlens/feedback",request -> request.bodyToMono(FeedbackStore.Request.class).flatMap(body -> request.exchange().getSession().flatMap(session -> {
                session.getAttributes().put("glyphlens-feedback",true);
                return feedback.submit(body,session.getId()).flatMap(receipt -> ServerResponse.accepted().contentType(MediaType.APPLICATION_JSON).bodyValue(receipt));
            })))
            .GET("/tools/glyphlens/{*path}",request -> file(request.pathVariable("path").replaceFirst("^/","")))
            .GET("/tools/char",request -> ServerResponse.temporaryRedirect(URI.create("/tools/glyphlens/")).build())
            .GET("/tools/char/",request -> ServerResponse.temporaryRedirect(URI.create("/tools/glyphlens/")).build())
            .build();
    }
    private Mono<ServerResponse> csrf(ServerRequest request) {
        Mono<CsrfToken> token=request.exchange().getAttribute(CsrfToken.class.getName());
        if(token==null) return json(Map.of("token","","headerName","X-XSRF-TOKEN"));
        return token.flatMap(value -> json(Map.of("token",value.getToken(),"headerName",value.getHeaderName())));
    }
    private Mono<ServerResponse> file(String name) {
        if(!FILES.contains(name)) return ServerResponse.notFound().build();
        String mime=name.endsWith(".html")?"text/html":name.endsWith(".css")?"text/css":name.endsWith(".js")?"text/javascript":name.endsWith(".svg")?"image/svg+xml":"text/plain";
        return ServerResponse.ok().contentType(new MediaType(MediaType.parseMediaType(mime),StandardCharsets.UTF_8)).cacheControl(CacheControl.noCache())
            .header("X-Content-Type-Options","nosniff").body(BodyInserters.fromResource(new ClassPathResource("web/"+name,PublicRoutes.class.getClassLoader())));
    }
    private static Mono<ServerResponse> json(Object value) { return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).bodyValue(value); }
}
