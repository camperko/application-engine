package com.netgrif.workflow.ipc

import com.netgrif.workflow.TestHelper
import com.netgrif.workflow.importer.service.Importer
import com.netgrif.workflow.petrinet.domain.VersionType
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService
import com.netgrif.workflow.startup.ImportHelper
import com.netgrif.workflow.startup.SuperCreator
import com.netgrif.workflow.workflow.domain.Case
import com.netgrif.workflow.workflow.domain.repositories.CaseRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension.class)
@ActiveProfiles(["test"])
@SpringBootTest
class AssignCancelFinishWithCaseTest {

    @Autowired
    private ImportHelper importHelper

    @Autowired
    private CaseRepository caseRepository

    @Autowired
    private Importer importer

    @Autowired
    private TestHelper testHelper

    @Autowired
    private IPetriNetService petriNetService;

    @Autowired
    private SuperCreator superCreator;

    private boolean initialised = false

    private def stream = { String name ->
        return AssignCancelFinishWithCaseTest.getClassLoader().getResourceAsStream(name)
    }

    @BeforeEach
    void setup() {
        if (!initialised) {
            testHelper.truncateDbs()
            initialised = true
        }
    }

    public static final String ASSIGN_CANCEL_FINISH_NET_FILE = "assign_cancel_finish_with_Case_test.xml"

    @Test
    void testAssignCancelFinish() {
        def testNet = petriNetService.importPetriNet(stream(ASSIGN_CANCEL_FINISH_NET_FILE), VersionType.MAJOR, superCreator.getLoggedSuper())

        assert testNet.isPresent()

        Case aCase = importHelper.createCase("Case 1", testNet.get())
        importHelper.assignTaskToSuper("Task", aCase.stringId)

        def cases = caseRepository.findAllByProcessIdentifier(testNet.get().identifier)
        assert cases.find { it.title == "Case 2" }.dataSet["field"].value == 1
    }
}