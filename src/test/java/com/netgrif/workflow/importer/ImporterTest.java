package com.netgrif.workflow.importer;

import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.repositories.PetriNetRepository;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService;
import groovy.util.GroovyTestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;

@SpringBootTest
@ActiveProfiles({"test"})
@RunWith(SpringRunner.class)
public class ImporterTest {

    @Autowired
    private Importer importer;

    @Autowired
    private PetriNetRepository repository;

    @Autowired
    private IWorkflowService workflowService;

    private static final String NET_TITLE = "jaxb_test";
    private static final String NET_INITIALS = "TST";
    private static final Integer NET_PLACES = 17;
    private static final Integer NET_TRANSITIONS = 22;
    private static final Integer NET_ARCS = 21;
    private static final Integer NET_FIELDS = 28;
    private static final Integer NET_ROLES = 3;

    @Before
    public void before() {
        repository.deleteAll();
    }

    @Test
    public void importPetriNet() throws Exception {
        importer.importPetriNet(new File("src/test/resources/prikladFM_test.xml"), NET_TITLE, NET_INITIALS);

        assertNetProperlyImported();
    }

    @Test
    public void priorityTest() {
        PetriNet net = importer.importPetriNet(new File("src/test/resources/priority_test.xml"), "Priority test", "PT");

        assert  net != null;

        Case useCase = workflowService.createCase(net.getStringId(), net.getTitle(), "color", 1L);

        assert useCase != null;
    }

    private void assertNetProperlyImported() {
        assert repository.count() == 1;
        PetriNet net = repository.findAll().get(0);
        assert net.getTitle().equals(NET_TITLE);
        assert net.getInitials().equals(NET_INITIALS);
        assert net.getPlaces().size() == NET_PLACES;
        assert net.getTransitions().size() == NET_TRANSITIONS;
        assert net.getArcs().size() == NET_ARCS;
        assert net.getDataSet().size() == NET_FIELDS;
        assert net.getRoles().size() == NET_ROLES;
    }
}