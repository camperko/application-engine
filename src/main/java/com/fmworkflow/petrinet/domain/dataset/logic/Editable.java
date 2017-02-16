package com.fmworkflow.petrinet.domain.dataset.logic;

import org.codehaus.jettison.json.JSONObject;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
final public class Editable extends LogicFunction {

    public Editable() {
        super();
        this.name = Editable.class.getName();
    }

    @Override
    public JSONObject unsafeApply(JSONObject jsonObject) throws Exception {
        return jsonObject.put("editable", true);
    }
}