package com.school.management.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class PdfGeneratorService {

    public PDDocument createDocument() {
        return new PDDocument();
    }

    public PDPage addPage(PDDocument document, PDRectangle pageSize) {
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        return page;
    }

    // Méthode addText existante modifiée pour prendre en charge l'arabe
    // Méthode addText originale
    public void addText(PDPageContentStream contentStream, String text, float x, float y, PDFont font, int fontSize) throws IOException {
        contentStream.setFont(font, fontSize);
        contentStream.beginText();
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
    }

    // Surcharge de addText pour le texte arabe avec gestion de l'alignement
    public void addText(PDPageContentStream contentStream, String text, float x, float y, PDFont font, int fontSize, boolean isArabic) throws IOException {
        contentStream.setFont(font, fontSize);
        contentStream.beginText();

        if (isArabic) {
            // Calcul de la largeur du texte et ajustement de l'alignement pour l'arabe
            float textWidth = font.getStringWidth(text) / 1000 * fontSize;
            contentStream.newLineAtOffset(x - textWidth, y);
        } else {
            // Alignement à gauche par défaut pour le français
            contentStream.newLineAtOffset(x, y);
        }

        contentStream.showText(text);
        contentStream.endText();
    }

    public void addImage(PDPageContentStream contentStream, PDDocument document, byte[] imageBytes, float x, float y, float width, float height) throws IOException {
        PDImageXObject image;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes)) {
            image = PDImageXObject.createFromByteArray(document, imageBytes, "logo");
        }
        contentStream.drawImage(image, x, y, width, height);
    }

    public ByteArrayInputStream saveDocument(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        document.close();
        return new ByteArrayInputStream(out.toByteArray());
    }
}

