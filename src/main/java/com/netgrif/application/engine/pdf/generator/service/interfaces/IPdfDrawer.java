package com.netgrif.application.engine.pdf.generator.service.interfaces;

import com.netgrif.application.engine.importer.model.DataType;
import com.netgrif.application.engine.pdf.generator.config.PdfResource;
import com.netgrif.application.engine.pdf.generator.domain.PdfField;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.util.List;

public interface IPdfDrawer {

    void setupDrawer(PDDocument pdf, PdfResource pdfResource);

    void setTemplatePdf(PDDocument pdf);

    void closeTemplate() throws IOException;

    void newPage() throws IOException;

    void checkOpenPages() throws IOException;

    void closeContentStream() throws IOException;

    void drawTitleField(PdfField field) throws IOException;

    void drawDataGroupField(PdfField field) throws IOException;

    void drawTextField(PdfField field) throws IOException;

    void drawBooleanField(PdfField field) throws IOException;

    void drawEnumerationField(PdfField field) throws IOException;

    void drawMultiChoiceField(PdfField field) throws IOException;

    void drawPageNumber() throws IOException;

    void drawBooleanBox(List<String> values, String text, int x, int y) throws IOException;

    boolean drawSelectionButton(List<String> values, String choice, int x, int y, DataType fieldType) throws IOException;

    void writeString(PDType0Font font, int fontSize, int x, int y, String text) throws IOException;

    void drawStroke(int x, int y, int fieldPosY, int width, int lineCounter, float strokeWidth) throws IOException;

}
