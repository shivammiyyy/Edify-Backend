package com.example.ragingestor.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class DocumentContentExtractor {

    public String extract(String filename, byte[] content) {
        String extension = extension(filename);
        try {
            String extracted = switch (extension) {
                case "txt", "md", "markdown" -> new String(content, StandardCharsets.UTF_8);
                case "pdf" -> extractPdf(content);
                case "docx" -> extractDocx(content);
                default -> throw new UnsupportedDocumentTypeException(
                        "Supported formats are PDF, TXT, Markdown, and DOCX");
            };
            if (extracted.isBlank()) {
                throw new InvalidDocumentException("The uploaded document contains no extractable text");
            }
            return extracted;
        } catch (IOException e) {
            throw new InvalidDocumentException("The uploaded document could not be parsed", e);
        }
    }

    public String contentType(String filename) {
        return switch (extension(filename)) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "md", "markdown" -> "text/markdown";
            case "txt" -> "text/plain";
            default -> throw new UnsupportedDocumentTypeException(
                    "Supported formats are PDF, TXT, Markdown, and DOCX");
        };
    }

    private static String extractPdf(byte[] content) throws IOException {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(content))) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String extractDocx(byte[] content) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
