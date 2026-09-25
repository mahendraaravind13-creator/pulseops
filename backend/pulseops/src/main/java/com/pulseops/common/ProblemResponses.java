package com.pulseops.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.net.URI;

/** Writes a problem document from a servlet filter, where @RestControllerAdvice does not apply. */
public final class ProblemResponses {

    private ProblemResponses() {
    }

    public static void write(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                             HttpStatus status, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
