package com.fmworkflow.petrinet.domain.dataset.logic;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class Visible implements LogicFunction {

    public Visible() {
        super();
    }

    @Override
    public ObjectNode unsafeApply(ObjectNode jsonObject) throws Exception {
        return jsonObject.put("visible", true);
    }
}
