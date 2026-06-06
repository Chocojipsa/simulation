package com.timedeal.seatreservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handleIOException(IOException ex) {
        String msg = ex.getMessage();
        if (msg != null && (msg.toLowerCase().contains("broken pipe") || msg.toLowerCase().contains("connection reset"))) {
            log.debug("Client disconnected during SSE stream: {}", msg);
        } else {
            log.error("I/O error occurred: {}", msg, ex);
        }
    }

    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handleAsyncRequestNotUsableException(org.springframework.web.context.request.async.AsyncRequestNotUsableException ex) {
        log.debug("Async request not usable (client disconnected): {}", ex.getMessage());
    }
}
