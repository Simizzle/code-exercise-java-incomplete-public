package com.urlshortener.controller;

import com.urlshortener.model.PaginatedResponse;
import com.urlshortener.model.ShortenUrlRequest;
import com.urlshortener.model.ShortenUrlResponse;
import com.urlshortener.model.UrlListItem;
import com.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@RestController
@org.springframework.web.bind.annotation.CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class UrlsController {

    private final UrlShortenerService service;

    public UrlsController(UrlShortenerService service) {
        this.service = service;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenUrlResponse> shorten(
            @Valid @RequestBody ShortenUrlRequest request,
            HttpServletRequest requestContext) {
        return ResponseEntity.ok().body(service.shorten(request, getBaseUrl(requestContext)));
    }

    @GetMapping("/urls")
    public PaginatedResponse<UrlListItem> getAll(
            HttpServletRequest requestContext,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be >= 0 and size must be between 1 and 100"
            );
        }
        var baseUrl = getBaseUrl(requestContext);
        return service.getAll(baseUrl, page, size);
    }

    @GetMapping("/{alias}")
    public ResponseEntity<Void> redirectToUrl(@PathVariable String alias) {
        var fullUrl = service.getFullUrl(alias);
        if (fullUrl == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).location(URI.create(fullUrl)).build();
    }

    @DeleteMapping("/{alias}")
    public ResponseEntity<Void> delete(@PathVariable String alias) {
        var deleted = service.delete(alias);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private String getBaseUrl(HttpServletRequest request) {
        var scheme = request.getScheme();
        var serverName = request.getServerName();
        var serverPort = request.getServerPort();
        var baseUrl = new StringBuilder(scheme).append("://").append(serverName);
        if (serverPort != 80 && serverPort != 443) {
            baseUrl.append(":").append(serverPort);
        }
        return baseUrl.toString();
    }
}
