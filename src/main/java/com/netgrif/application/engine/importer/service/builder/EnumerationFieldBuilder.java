package com.netgrif.application.engine.importer.service.builder;

import com.netgrif.application.engine.importer.model.Data;
import com.netgrif.application.engine.importer.model.DataType;
import com.netgrif.application.engine.importer.service.Importer;
import com.netgrif.application.engine.petrinet.domain.I18nString;
import com.netgrif.application.engine.petrinet.domain.dataset.EnumerationField;
import org.springframework.stereotype.Component;

@Component
public class EnumerationFieldBuilder extends FieldBuilder<EnumerationField> {
    @Override
    public EnumerationField build(Data data, Importer importer) {
        EnumerationField field = new EnumerationField();
        if (data.getOptions() != null) {
            setFieldOptions(field, data, importer);
        } else {
            setFieldChoices(field, data, importer);
        }
        setDefaultValue(field, data, init -> {
            if (init != null && !init.equals("")) {
                field.setDefaultValue(new I18nString(init));
            }
        });
        return field;
    }

    @Override
    public DataType getType() {
        return DataType.ENUMERATION;
    }
}
