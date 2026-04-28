package com.buyapi.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.buyapi.exception.BadRequestException;

import lombok.extern.slf4j.Slf4j;

/**
 * Uploads product images to Supabase Storage using the REST API.
 *
 * Required environment variables:
 *   SUPABASE_URL        — e.g. https://xxxxxxxxxxxx.supabase.co
 *   SUPABASE_KEY        — secret key (sb_secret_...) from Settings → API Keys
 *   SUPABASE_BUCKET     — storage bucket name (default: products)
 */
@Slf4j
@Service
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @Value("${supabase.bucket:products}")
    private String bucket;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Uploads a file to Supabase Storage and returns its public URL.
     *
     * @param path     the object path within the bucket (e.g. "product-1-uuid.jpg")
     * @param file     the multipart file to upload
     * @return         the public URL of the uploaded file
     */
    public String upload(String path, MultipartFile file) throws IOException {
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("apikey", supabaseKey)
                .header("Content-Type", contentType)
                .header("x-upsert", "true")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Supabase upload failed — status: {}, body: {}", response.statusCode(), response.body());
                throw new BadRequestException("Image upload failed. Please try again.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Image upload interrupted", e);
        }

        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + path;
    }
}