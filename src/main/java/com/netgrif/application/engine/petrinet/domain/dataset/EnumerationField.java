package com.netgrif.application.engine.petrinet.domain.dataset;

import com.netgrif.application.engine.importer.model.DataType;
import com.netgrif.application.engine.petrinet.domain.I18nString;

import java.util.List;
import java.util.Locale;

public class EnumerationField extends ChoiceField<I18nString> {

    public EnumerationField() {
        super();
    }

    public EnumerationField(List<I18nString> values) {
        super(values);
    }

    @Override
    public DataType getType() {
        return DataType.ENUMERATION;
    }

    public String getTranslatedValue(Locale locale) {
        if (this.getValue() == null) {
            return null;
        }
        return getValue().getTranslation(locale);
    }

    @Override
    public EnumerationField clone() {
        EnumerationField clone = new EnumerationField();
        super.clone(clone);
        clone.choices = this.choices;
        clone.choicesExpression = this.choicesExpression;
        return clone;
    }
}