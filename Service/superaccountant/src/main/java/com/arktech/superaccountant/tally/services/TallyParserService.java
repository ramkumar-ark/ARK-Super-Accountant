package com.arktech.superaccountant.tally.services;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.arktech.superaccountant.tally.models.TallyMessage;
import com.arktech.superaccountant.tally.models.Voucher;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TallyParserService {

    private final ObjectMapper objectMapper;

    public TallyParserService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public TallyMessage parseJson(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String jsonContent = convertToUtf8String(bytes);
        return objectMapper.readValue(jsonContent, TallyMessage.class);
    }

    public Map<String, List<Voucher>> groupByVoucherType(TallyMessage message) {
        if (message.getTallymessage() == null) {
            return Map.of();
        }
        return message.getTallymessage().stream()
                .filter(v -> v.getVouchertypename() != null)
                .collect(Collectors.groupingBy(Voucher::getVouchertypename));
    }

    /**
     * Detects encoding from BOM (Byte Order Mark) and converts to UTF-8 string.
     * Supports UTF-16 LE, UTF-16 BE, and falls back to UTF-8.
     */
    private String convertToUtf8String(byte[] bytes) {
        Charset charset = detectCharset(bytes);
        String content = new String(bytes, charset);
        // Strip BOM character if present after decoding
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        return content;
    }

    private Charset detectCharset(byte[] bytes) {
        if (bytes.length >= 2) {
            // UTF-16 LE BOM: FF FE
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
            // UTF-16 BE BOM: FE FF
            if ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
                return StandardCharsets.UTF_16BE;
            }
        }
        if (bytes.length >= 3) {
            // UTF-8 BOM: EF BB BF
            if ((bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                return StandardCharsets.UTF_8;
            }
        }
        // Default to UTF-8
        return StandardCharsets.UTF_8;
    }
}
