package com.netgrif.workflow.workflow.service;

import com.netgrif.workflow.auth.domain.Authority;
import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.domain.repositories.AuthorityRepository;
import com.netgrif.workflow.importer.Importer;
import com.netgrif.workflow.petrinet.domain.repositories.PetriNetRepository;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.repositories.CaseRepository;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.util.Collections;
import java.util.List;

@SpringBootTest
@ActiveProfiles({"test"})
@RunWith(SpringRunner.class)
public class CaseMonitorTest {

    @Autowired
    private IWorkflowService workflowService;

    @Autowired
    private CaseRepository repository;

    @Autowired
    private PetriNetRepository netRepository;

    @Autowired
    private Importer importer;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Before
    public void setUp() {
        repository.deleteAll();
    }

    @Test
    public void afterFindOne() throws Exception {
        importer.importPetriNet(new File("src/test/resources/prikladFM.xml"), "net", "NET");
        workflowService.createCase(netRepository.findAll().get(0).getStringId(), "Storage Unit", "color-fg-fm-500", mockLoggedUser());

        List<Case> cases = repository.findAll();
        Case useCase = repository.findOne(cases.get(0).getStringId());

        assert !useCase.getPetriNet().isNotInitialized();
    }

    public LoggedUser mockLoggedUser(){
        Authority authorityUser;
        if (authorityRepository.count() > 0)
            authorityUser = authorityRepository.findAll().get(0);
        else
            authorityUser = authorityRepository.save(new Authority(Authority.user));
        return new LoggedUser(1L, "super@netgrif.com","password", Collections.singleton(authorityUser));
    }
}