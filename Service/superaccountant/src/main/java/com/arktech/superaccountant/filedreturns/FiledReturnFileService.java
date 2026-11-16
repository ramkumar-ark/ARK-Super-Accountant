package com.arktech.superaccountant.filedreturns;

import com.arktech.superaccountant.filedreturns.FiledReturnValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FiledReturnFileService {

    private static final long MAX_SIZE = 10L * 1024 * 1024;

    @Autowired
    private FiledReturnFileRepository repository;

    public FiledReturnFile store(UUID org, String actor, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FiledReturnValidationException("Uploaded file is empty");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new FiledReturnValidationException("File exceeds the 10 MB limit");
        }
        byte[] content;
        try (InputStream in = file.getInputStream()) {
            content = in.readAllBytes();
        } catch (IOException e) {
            throw new FiledReturnValidationException("Could not read the uploaded file");
        }
        String kind = sniff(content);
        if (kind == null) {
            throw new FiledReturnValidationException("File must be a PDF or a JSON document");
        }
        try {
            FiledReturnFile entity = new FiledReturnFile();
            entity.setOrganizationId(org);
            entity.setFileName(file.getOriginalFilename());
            entity.setContentType(file.getContentType());
            entity.setSha256(sha256(content));
            entity.setContent(content);
            entity.setUploadedBy(actor);
            return repository.save(entity);
        } catch (IOException e) {
            throw new FiledReturnValidationException("Could not read the uploaded file");
        }
    }

    private String sniff(byte[] content) {
        int i = 0;
        while (i < content.length && Character.isWhitespace((char) (content[i] & 0xFF))) {
            i++;
        }
        if (i + 4 <= content.length
                && content[i] == '%' && content[i + 1] == 'P' && content[i + 2] == 'D' && content[i + 3] == 'F') {
            return "pdf";
        }
        if (i < content.length && content[i] == '{') {
            return "json";
        }
        return null;
    }

    private String sha256(byte[] content) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
