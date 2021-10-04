package com.netgrif.workflow.configuration.drools.interfaces;

import com.netgrif.workflow.configuration.drools.RuleEngineGlobal;
import org.kie.api.runtime.KieSession;

import java.util.List;

public interface IRuleEngineGlobalsProvider {

    List<RuleEngineGlobal> globals();

    List<String> imports();

    void setGlobals(KieSession session);
}
