package com.netgrif.workflow.workflow

import com.netgrif.workflow.TestHelper
import com.netgrif.workflow.auth.service.interfaces.IUserService
import com.netgrif.workflow.petrinet.domain.PetriNet
import com.netgrif.workflow.petrinet.domain.VersionType
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService
import com.netgrif.workflow.startup.ImportHelper
import com.netgrif.workflow.workflow.domain.Case
import com.netgrif.workflow.workflow.domain.QTask
import com.netgrif.workflow.workflow.domain.Task
import com.netgrif.workflow.workflow.service.interfaces.ITaskService
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
class TaskRefInitTest {

    @Autowired
    private ITaskService taskService

    @Autowired
    private ImportHelper helper

    @Autowired
    private IPetriNetService petriNetService

    @Autowired
    private IUserService userService

    @Autowired
    private TestHelper testHelper

    PetriNet net = null
    PetriNet autoTrigger = null

    @Before
    void initNet() {
        testHelper.truncateDbs()
        net = petriNetService.importPetriNet(new FileInputStream("src/test/resources/taskref_init.xml"), VersionType.MAJOR, userService.loggedOrSystem.transformToLoggedUser()).get()
        autoTrigger = petriNetService.importPetriNet(new FileInputStream("src/test/resources/autotrigger_taskref.xml"), VersionType.MAJOR, userService.loggedOrSystem.transformToLoggedUser()).get()
        assert net != null
    }

    @Test
    void testInitValue() {
        Case aCase = helper.createCase("Test task ref init", net)
        Task task1 = taskService.searchOne(QTask.task.caseTitle.eq("Test task ref init") & QTask.task.transitionId.eq("t1"))
        Task task2 = taskService.searchOne(QTask.task.caseTitle.eq("Test task ref init") & QTask.task.transitionId.eq("t3"))

        assert ((List<String>)aCase.dataSet.get("taskRef_0").value).containsAll(Arrays.asList(task1.stringId, task2.stringId))
        assert ((List<String>)aCase.dataSet.get("taskRef_1").value).isEmpty()
        assert ((List<String>)aCase.dataSet.get("taskRef_2").value).contains(task1.stringId) & ((List<String>)aCase.dataSet.get("taskRef_2").value).size() == 1
        assert ((List<String>)aCase.dataSet.get("taskRef_3").value).isEmpty()
    }

    @Test
    void autoTriggerTaskRef() {
        Case bCase = helper.createCase("Task ref init with auto trigger", autoTrigger)
        assert ((List<String>) bCase.dataSet["tema"].value).contains(bCase.tasks.stream().filter({ t -> t.transition == "t1" }).findFirst().get().task) && ((List<String>) bCase.dataSet["tema"].value).size() == 1
    }
}
