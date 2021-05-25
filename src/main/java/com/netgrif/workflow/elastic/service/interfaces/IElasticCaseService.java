package com.netgrif.workflow.elastic.service.interfaces;

import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.elastic.domain.ElasticCase;
import com.netgrif.workflow.elastic.web.requestbodies.CaseSearchRequest;
import com.netgrif.workflow.workflow.domain.Case;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface IElasticCaseService {

    void removeByPetriNetIdNow(String processId);

    @Async
    void index(ElasticCase useCase);

    void indexNow(ElasticCase useCase);

    Page<Case> search(List<CaseSearchRequest> requests, LoggedUser user, Pageable pageable, Locale locale, Boolean isIntersection);

    long count(List<CaseSearchRequest> requests, LoggedUser user, Locale locale, Boolean isIntersection);

    Map<String, Float> fullTextFields();

    void remove(String caseId);

    void removeByPetriNetId(String processId);
}