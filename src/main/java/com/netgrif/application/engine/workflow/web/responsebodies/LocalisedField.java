package com.netgrif.application.engine.workflow.web.responsebodies;

import com.netgrif.application.engine.importer.model.DataType;
import com.netgrif.application.engine.petrinet.domain.Component;
import com.netgrif.application.engine.petrinet.domain.Format;
import com.netgrif.application.engine.petrinet.domain.dataset.Field;
import com.netgrif.application.engine.petrinet.domain.dataset.logic.FieldBehavior;
import com.netgrif.application.engine.petrinet.domain.dataset.logic.FieldLayout;
import com.netgrif.application.engine.petrinet.domain.dataset.logic.validation.LocalizedValidation;
import com.netgrif.application.engine.petrinet.domain.dataset.logic.validation.Validation;
import com.netgrif.application.engine.petrinet.domain.views.View;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Data
@NoArgsConstructor
public class LocalisedField {

    private String stringId;

    private DataType type;

    private String name;

    private String description;

    private String placeholder;

    private Set<FieldBehavior> behavior;

    private FieldLayout layout;

    private Object value;

    private Long order;

    private Format formatFilter;

    private View view;

    private Integer length;

    private Component component;

    private List<LocalizedValidation> validations;

    private String parentTaskId;

    private String parentCaseId;

    public LocalisedField(Field field, Locale locale) {
        stringId = field.getStringId();
        type = field.getType();
        name = field.getTranslatedName(locale);
        description = field.getTranslatedDescription(locale);
        placeholder = field.getTranslatedPlaceholder(locale);
        behavior = field.getBehavior();
        layout = field.getLayout();
        value = field.getValue();
        order = field.getOrder();
        formatFilter = field.getFormat();
        view = field.getView();
        length = field.getLength();
        component = field.getComponent();
        validations = loadValidations(field, locale);
        parentTaskId = field.getParentTaskId();
        parentCaseId = field.getParentCaseId();
    }

    private List<LocalizedValidation> loadValidations(Field field, Locale locale) {
        List<LocalizedValidation> locVal = new ArrayList<>();
        List<Validation> fieldValidations = field.getValidations();
        if (fieldValidations != null) {
            fieldValidations.forEach(val -> locVal.add(val.getLocalizedValidation(locale)));
        }
        return locVal;
    }
}