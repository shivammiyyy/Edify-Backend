package com.example.ragingestor.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DocumentContentExtractorTest {

    private final DocumentContentExtractor extractor = new DocumentContentExtractor();

    @Test
    void extractsPlainTextAndMarkdownAsUtf8() {
        byte[] content = "Policy - draft".getBytes(StandardCharsets.UTF_8);

        assertEquals("Policy - draft", extractor.extract("POLICY.MD", content));
        assertEquals("text/markdown", extractor.contentType("POLICY.MD"));
    }

    @Test
    void extractsPdfText() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0))) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("PDF policy text");
                stream.endText();
            }
            document.save(output);
            pdf = output.toByteArray();
        }

        assertTrue(extractor.extract("policy.pdf", pdf).contains("PDF policy text"));
    }

    @Test
    void extractsDocxText() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("DOCX handbook");
            document.write(output);
            docx = output.toByteArray();
        }

        assertTrue(extractor.extract("handbook.docx", docx).contains("DOCX handbook"));
    }

    @Test
    void rejectsUnsupportedAndUnparseableDocuments() {
        assertThrows(UnsupportedDocumentTypeException.class, () -> extractor.extract("archive.zip", new byte[]{1}));
        assertThrows(InvalidDocumentException.class, () -> extractor.extract("broken.pdf", new byte[]{1, 2, 3}));
        assertThrows(InvalidDocumentException.class, () -> extractor.extract("empty.txt", new byte[0]));
    }
}
