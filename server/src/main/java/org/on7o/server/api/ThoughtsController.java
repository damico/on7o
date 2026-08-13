package org.on7o.server.api;

import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Read-only access to what has been captured so far. */
@RestController
public class ThoughtsController {

    private final ThoughtStore store;

    public ThoughtsController(ThoughtStore store) {
        this.store = store;
    }

    @GetMapping("/api/thoughts")
    public List<Thought> list(@RequestParam(defaultValue = "50") int limit) throws IOException {
        return store.list(Math.clamp(limit, 1, 1000));
    }

    @GetMapping("/api/thoughts/{id}")
    public ResponseEntity<Thought> get(@PathVariable String id) {
        return ResponseEntity.of(store.find(id));
    }

    @GetMapping("/api/thoughts/{id}/audio")
    public ResponseEntity<Resource> audio(@PathVariable String id) {
        return store.find(id)
                .map(thought -> {
                    Path path = store.audioPath(thought);
                    if (!Files.isReadable(path)) {
                        return ResponseEntity.notFound().<Resource>build();
                    }
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType("audio/wav"))
                            .contentLength(thought.audioBytes())
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "inline; filename=\"" + thought.id() + ".wav\"")
                            .body((Resource) new FileSystemResource(path));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
