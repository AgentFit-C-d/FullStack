package com.agentfit.coreapi.domain.project.service;

import com.agentfit.coreapi.domain.project.entity.PlanningInput;
import java.io.IOException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PlanningInputValidator {
    public static final long MAX_BYTES = 10_485_760L;
    public static final int MAX_CODE_POINTS = 100_000;
    public PlanningInput validate(String projectId, long revision, MultipartFile file, String inputText) {
        if ((file == null || file.isEmpty()) == (inputText == null || inputText.isBlank())) throw new IllegalArgumentException("Exactly one planning input is required");
        if (file != null && !file.isEmpty()) return validateFile(projectId, revision, file);
        String text = inputText;
        if (text.codePointCount(0, text.length()) > MAX_CODE_POINTS) throw new IllegalArgumentException("Input text exceeds 100,000 code points");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new PlanningInput(projectId, revision, "TEXT", null, "text/plain", (long) bytes.length, text.codePointCount(0,text.length()), null, bytes, text, null, Instant.now());
    }
    private PlanningInput validateFile(String projectId,long revision,MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Input file exceeds 10 MiB");
            String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
            boolean pdf = type.equals("application/pdf") || file.getOriginalFilename()!=null && file.getOriginalFilename().toLowerCase().endsWith(".pdf");
            boolean markdown = type.equals("text/markdown") || file.getOriginalFilename()!=null && file.getOriginalFilename().toLowerCase().matches(".*\\.(md|markdown)$");
            if (!pdf && !markdown) throw new IllegalArgumentException("Only PDF or Markdown files are supported");
            String extracted; Integer pages = null;
            if (pdf) {
                try (var doc = Loader.loadPDF(bytes)) { pages = doc.getNumberOfPages(); if (pages > 100) throw new IllegalArgumentException("PDF exceeds 100 pages"); extracted = new PDFTextStripper().getText(doc); }
            } else extracted = new String(bytes, StandardCharsets.UTF_8);
            int count = extracted.codePointCount(0, extracted.length());
            if (count > MAX_CODE_POINTS) throw new IllegalArgumentException("Extracted text exceeds 100,000 code points");
            return new PlanningInput(projectId,revision,pdf?"PDF":"MARKDOWN",file.getOriginalFilename(),pdf?"application/pdf":"text/markdown",(long)bytes.length,count,pages,bytes,extracted,null,Instant.now());
        } catch (IOException e) { throw new IllegalArgumentException("Unable to read planning input", e); }
    }
}
