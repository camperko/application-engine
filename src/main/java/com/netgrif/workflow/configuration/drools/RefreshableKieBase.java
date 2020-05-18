package com.netgrif.workflow.configuration.drools;

import com.netgrif.workflow.rules.domain.RuleRepository;
import org.kie.api.KieBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RefreshableKieBase {

    private LocalDateTime lastRefresh;
    private KieBase kieBase;

    private RuleRepository ruleRepository;
    private KnowledgeBaseInitializer knowledgeBaseInitializer;


    public RefreshableKieBase(@Autowired RuleRepository ruleRepository, @Autowired KnowledgeBaseInitializer knowledgeBaseInitializer) {
        this.ruleRepository = ruleRepository;
        this.knowledgeBaseInitializer = knowledgeBaseInitializer;
        refresh();
    }

    public KieBase kieBase() {
        return kieBase;
    }

    public boolean shouldRefresh() {
        return ruleRepository.existsByLastUpdateAfter(lastRefresh);
    }

    public void refresh() {
        this.lastRefresh = LocalDateTime.now();
        this.kieBase = knowledgeBaseInitializer.constructKieBase();
    }
}
