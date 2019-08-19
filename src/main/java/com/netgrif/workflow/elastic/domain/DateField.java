package com.netgrif.workflow.elastic.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DateField extends DataField<String> {

    private long timestamp;

    public DateField(String value, long timestamp) {
        super(value);
        this.timestamp = timestamp;
    }
}