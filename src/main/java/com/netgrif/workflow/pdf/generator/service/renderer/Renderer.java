package com.netgrif.workflow.pdf.generator.service.renderer;

import com.netgrif.workflow.pdf.generator.config.PdfResource;
import com.netgrif.workflow.pdf.generator.domain.PdfField;
import com.netgrif.workflow.pdf.generator.service.interfaces.IPdfDrawer;
import lombok.Data;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.encoding.WinAnsiEncoding;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.List;

@Data
public abstract class Renderer {

    protected IPdfDrawer pdfDrawer;

    protected PdfResource resource;

    int marginLeft, marginBottom, marginTop;

    int lineHeight, pageDrawableWidth, padding, pageHeight, baseX;
    int fontValueSize, fontLabelSize, fontTitleSize;

    float strokeWidth;

    public abstract int renderLabel(PdfField field) throws IOException;

    public void setupRenderer(IPdfDrawer pdfDrawer, PdfResource resource) {
        this.pdfDrawer = pdfDrawer;
        this.resource = resource;
        this.marginLeft = resource.getMarginLeft();
        this.marginBottom = resource.getMarginBottom();
        this.marginTop = resource.getMarginTop();
        this.lineHeight = resource.getLineHeight();
        this.pageDrawableWidth = resource.getPageDrawableWidth();
        this.padding = resource.getPadding();
        this.baseX = resource.getBaseX();
        this.pageHeight = resource.getPageHeight();
        this.fontValueSize = resource.getFontValueSize();
        this.fontLabelSize = resource.getFontLabelSize();
        this.fontTitleSize = resource.getFontTitleSize();
        this.strokeWidth = resource.getStrokeWidth();
    }


    protected static int getTextWidth(List<String> values, PDType0Font font, int fontSize) throws IOException {
        int result = 0;
        for (String value : values) {
            String formattedValue = removeUnsupportedChars(value);
            if (result < font.getStringWidth(formattedValue) / 1000 * fontSize)
                result = (int) (font.getStringWidth(formattedValue) / 1000 * fontSize);
        }
        return result;
    }

    protected int getMaxLabelLineSize(int fieldWidth, int fontSize) {
        return (int) ((fieldWidth - padding) * resource.getSizeMultiplier() / fontSize);
    }

    public static String removeUnsupportedChars(String input) {
        String value = Jsoup.parse(input.replaceAll("\\s{1,}", " ")).text();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (WinAnsiEncoding.INSTANCE.contains(value.charAt(i))) {
                b.append(value.charAt(i));
            }
        }
        return b.toString();
    }
}
