package com.netgrif.workflow.petrinet.domain.dataset

import com.netgrif.workflow.TestHelper
import com.netgrif.workflow.petrinet.domain.PetriNet
import com.netgrif.workflow.petrinet.domain.VersionType
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService
import com.netgrif.workflow.startup.SuperCreator
import com.netgrif.workflow.workflow.domain.Case
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension

@SpringBootTest
@ActiveProfiles(["test"])
@ExtendWith(SpringExtension.class)
class DynamicCaseNameTest {

    @Autowired
    private TestHelper testHelper

    @Autowired
    private IWorkflowService workflowService

    @Autowired
    private IPetriNetService petriNetService

    @Autowired
    private SuperCreator superCreator

    @BeforeEach
    void before() {
        testHelper.truncateDbs()
    }

    @Test
    void testInitValues() {
        Optional<PetriNet> optNet = petriNetService.importPetriNet(new FileInputStream("src/test/resources/petriNets/dynamic_case_name_test.xml"), VersionType.MAJOR, superCreator.getLoggedSuper())
        Case useCase = workflowService.createCase(optNet.get().stringId, null, "", superCreator.loggedSuper, Locale.forLanguageTag("sk-SK"))
        assert useCase.title == "SK text value 6"

        Case useCase2 = workflowService.createCase(optNet.get().stringId, null, "", superCreator.loggedSuper, Locale.ENGLISH)
        assert useCase2.title == "EN text value 6"
    }
}
