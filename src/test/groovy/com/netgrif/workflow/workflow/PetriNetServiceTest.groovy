package com.netgrif.workflow.workflow

import com.netgrif.workflow.TestHelper
import com.netgrif.workflow.auth.service.interfaces.IUserProcessRoleService
import com.netgrif.workflow.auth.service.interfaces.IUserService
import com.netgrif.workflow.ipc.TaskApiTest
import com.netgrif.workflow.petrinet.domain.PetriNet
import com.netgrif.workflow.petrinet.domain.repositories.PetriNetRepository
import com.netgrif.workflow.petrinet.domain.roles.ProcessRoleRepository
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService
import com.netgrif.workflow.startup.ImportHelper
import com.netgrif.workflow.startup.SuperCreator
import com.netgrif.workflow.utils.FullPageRequest
import com.netgrif.workflow.workflow.domain.repositories.TaskRepository
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner.class)
@ActiveProfiles(["test"])
@SpringBootTest
class PetriNetServiceTest {

    public static final String NET_FILE = "process_delete_test.xml"

    @Autowired
    private ImportHelper importHelper

    @Autowired
    private TestHelper testHelper

    @Autowired
    private SuperCreator superCreator

    @Autowired
    private IPetriNetService petriNetService

    @Autowired
    private IWorkflowService workflowService

    @Autowired
    private TaskRepository taskRepository

    @Autowired
    private IUserProcessRoleService userProcessRoleService

    @Autowired
    private IUserService userService

    @Autowired
    private PetriNetRepository petriNetRepository

    @Autowired
    private ProcessRoleRepository processRoleRepository

    private def stream = { String name ->
        return TaskApiTest.getClassLoader().getResourceAsStream(name)
    }

    @Before
    void setup() {
        testHelper.truncateDbs()
    }

    @Test
    void processDelete() {
        int processRoleCount = processRoleRepository.findAll().size()

        Optional<PetriNet> testNetOptional = petriNetService.importPetriNet(stream(NET_FILE), "major", superCreator.getLoggedSuper())
        assert testNetOptional.isPresent()
        assert petriNetRepository.findAll().size() == 1
        PetriNet testNet = testNetOptional.get()
        importHelper.createCase("Case 1", testNet)

        assert workflowService.getAll(new FullPageRequest()).size() == 1
        assert taskRepository.findAll().size() == 2
        assert processRoleRepository.findAll().size() == processRoleCount + 2

        def user = userService.findByEmail("user@netgrif.com", false)
        assert user != null
        assert user.processRoles.size() == 0

        userService.addRole(user, testNet.roles.values().collect().get(0).stringId)
        user = userService.findByEmail("user@netgrif.com", false)
        assert user != null
        assert user.processRoles.size() == 1


        petriNetService.deletePetriNet(testNet.stringId)
        assert petriNetRepository.findAll().size() == 0
        assert workflowService.getAll(new FullPageRequest()).size() == 0
        assert taskRepository.findAll().size() == 0
        assert processRoleRepository.findAll().size() == processRoleCount
        user = userService.findByEmail("user@netgrif.com", false)
        assert user != null
        assert user.processRoles.size() == 0
    }
}
