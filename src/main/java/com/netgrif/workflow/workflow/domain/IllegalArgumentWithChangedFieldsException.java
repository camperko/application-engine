package com.netgrif.workflow.workflow.domain;

import com.netgrif.workflow.workflow.domain.eventoutcomes.EventOutcome;
import lombok.Getter;
import lombok.Setter;

public class IllegalArgumentWithChangedFieldsException extends IllegalArgumentException {

    @Getter
    @Setter
    private EventOutcome outcome;

    public IllegalArgumentWithChangedFieldsException(String var1, EventOutcome outcome) {
        super(var1);
        this.outcome = outcome;
    }
}
