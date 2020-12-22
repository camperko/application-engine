package com.netgrif.workflow.workflow.domain;

import com.netgrif.workflow.petrinet.domain.dataset.logic.ChangedField;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

public class IllegalArgumentWithChangedFieldsException extends IllegalArgumentException {

    @Getter @Setter
    private Map<String, ChangedField> changedFields;

    public IllegalArgumentWithChangedFieldsException(String var1, Map<String, ChangedField> changedFields) {
        super(var1);
        this.changedFields = changedFields;
    }
}