package com.netgrif.workflow.workflow.service;

import com.netgrif.workflow.petrinet.domain.dataset.logic.action.runner.CaseFieldsExpressionRunner;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.service.interfaces.IDataValidationExpressionEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataValidationExpressionEvaluator implements IDataValidationExpressionEvaluator {

    @Autowired
    protected CaseFieldsExpressionRunner runner;

    @Override
    public String compile(Case useCase, String expression) {
        return runner.run(useCase, "\"" + expression + "\"").toString();
    }

}
