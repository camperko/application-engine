package com.netgrif.workflow.petrinet.domain.dataset.logic.action.runner


import com.netgrif.workflow.petrinet.domain.dataset.logic.action.ActionDelegate
import com.netgrif.workflow.workflow.domain.Case
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import javax.inject.Provider

@Component
class CaseFieldsExpressionRunner extends FieldsExpressionRunner<Case> {

    @Autowired
    private Provider<ActionDelegate> delegateProvider

    CaseFieldsExpressionRunner(@Value('${expressions.runner.cache-size}') int cacheSize) {
        super(cacheSize)
    }

    protected void initCode(Object delegate, Case useCase, Map<String, String> fields) {
        ActionDelegate ad = ((ActionDelegate) delegate)
        ad.useCase = useCase
        ad.petriNet = useCase.petriNet
        ad.initFieldsMap(fields)
    }

    @Override
    protected Map<String, String> getFieldIds(Case useCase) {
        Map<String, String> result = useCase.dataSet.keySet().collectEntries {[(it): (it)]} as Map<String, String>
        result.putAll(useCase.petriNet.staticDataSet.keySet().collectEntries {[(it): (it)]} as Map<String, String>)
        return result
    }

    @Override
    protected ActionDelegate actionDelegate() {
        return delegateProvider.get()
    }
}
