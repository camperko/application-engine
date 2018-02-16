package com.netgrif.workflow.petrinet.domain.dataset

import org.springframework.data.mongodb.core.mapping.Document

@Document
class BooleanField extends FieldWithDefault<Boolean> {

    BooleanField() {
        super()
        super.defaultValue = false
    }

    @Override
    FieldType getType() {
        return FieldType.BOOLEAN
    }

    @Override
    void clearValue() {
        super.clearValue()
        setValue(getDefaultValue())
    }

    Boolean or(final BooleanField field) {
        return this.value || field.value
    }

    Boolean and(final BooleanField field) {
        return this.value && field.value
    }
}
