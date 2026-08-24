package org.on7o.server.api;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.on7o.server.ingest.CaptureTooLargeException;
import org.on7o.server.ingest.ThoughtNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the failures the API can produce into one predictable response shape,
 * RFC 7807 {@link ProblemDetail}, so that callers never have to parse a stack
 * trace or a Jackson message to find out what they got wrong.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CaptureTooLargeException.class)
    public ProblemDetail tooLarge(CaptureTooLargeException e) {
        log.warn("rejected capture: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage());
    }

    @ExceptionHandler(ThoughtNotFoundException.class)
    public ProblemDetail notFound(ThoughtNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * A body that parsed but broke its constraints. Every offending field is
     * reported at once under {@code errors}, so a caller fixing a fixture does
     * not have to discover the problems one request at a time.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError field : e.getBindingResult().getFieldErrors()) {
            errors.put(field.getField(), field.getDefaultMessage());
        }

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "the request body is invalid");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);

        log.debug("rejected request body: {}", errors);
        return problem;
    }

    /** A body that could not be parsed at all, most often a malformed timestamp. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadableBody(HttpMessageNotReadableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, describe(e));
        problem.setTitle("Invalid request");
        return problem;
    }

    /** Names the field Jackson choked on when it is known, since the raw message is not caller-facing. */
    private static String describe(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof InvalidFormatException cause) {
            List<InvalidFormatException.Reference> path = cause.getPath();
            String field = path.isEmpty() ? "body" : path.get(path.size() - 1).getFieldName();
            return "invalid value for " + field + ": " + cause.getValue();
        }
        return "the request body could not be read as JSON";
    }
}
