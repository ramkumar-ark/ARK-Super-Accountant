package com.arktech.superaccountant.login.controllers;

import com.arktech.superaccountant.login.payload.response.MessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<MessageResponse> handleMultipart(MultipartException e) {
        return ResponseEntity.badRequest().body(new MessageResponse("Current request is not a multipart request"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<MessageResponse> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(new MessageResponse("Required part '" + e.getRequestPartName() + "' is not present."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<MessageResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(new MessageResponse("Required parameter '" + e.getParameterName() + "' is not present."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MessageResponse> handleNotReadable(HttpMessageNotReadableException e) {
        String cause = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        return ResponseEntity.badRequest().body(new MessageResponse("Malformed request body: " + cause));
    }
}
