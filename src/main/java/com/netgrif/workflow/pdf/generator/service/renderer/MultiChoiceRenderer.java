package com.netgrif.workflow.pdf.generator.service.renderer;

import com.netgrif.workflow.pdf.generator.domain.PdfField;
import com.netgrif.workflow.petrinet.domain.dataset.FieldType;
import com.netgrif.workflow.petrinet.domain.dataset.MultichoiceField;

import java.io.IOException;
import java.util.List;

public class MultiChoiceRenderer extends SelectionFieldRenderer<MultichoiceField> {

    @Override
    public void setFieldParams(PdfField field) {
        helperField = new PdfField(field.getFieldId(),field.getLabel(), field.getValues(), field.getChoices(), field.getType(), resource.getBaseX() + field.getX(),
                resource.getBaseY() - field.getBottomY(), field.getWidth(), field.getHeight());
    }

    @Override
    public int renderLabel(PdfField field) throws IOException {
        setFieldParams(field);
        return renderLabel(helperField, resource.getLabelFont(), fontLabelSize);
    }

    @Override
    public void renderValue(PdfField field, int lineCounter) throws IOException {
        setFieldParams(field);
        super.renderValue(helperField, lineCounter);
    }
}
