package com.fmworkflow.petrinet.domain.dataset.logic;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.function.Function;

public interface LogicFunction extends Function<ObjectNode, ObjectNode> {

    @Override
    default ObjectNode apply(ObjectNode jsonObject) {
        try {
            return unsafeApply(jsonObject);
        } catch (Exception e) {
            throw new LogicNotApplicableException(e);
        }
    }

    ObjectNode unsafeApply(ObjectNode jsonObject) throws Exception;
}
