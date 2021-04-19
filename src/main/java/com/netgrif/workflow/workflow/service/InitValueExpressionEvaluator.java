package com.netgrif.workflow.workflow.service;

import com.netgrif.workflow.petrinet.domain.dataset.Field;
import com.netgrif.workflow.petrinet.domain.dataset.logic.action.runner.CaseFieldsExpressionRunner;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.service.interfaces.IInitValueExpressionEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InitValueExpressionEvaluator implements IInitValueExpressionEvaluator {

    @Autowired
    private CaseFieldsExpressionRunner runner;

    @Override
    public Object evaluate(Case useCase, Field defaultField) {
        return runner.run(useCase, defaultField.getInitExpression());
    }
}
