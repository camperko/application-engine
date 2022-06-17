package com.netgrif.application.engine.workflow.web.responsebodies;

import com.netgrif.application.engine.workflow.domain.DataField;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
public class DataSet {

    /**
     * Field ID: DataField
     */
    private Map<String, DataField> fields;

    public DataSet() {
        this.fields = new HashMap<>();
    }
}
