package com.netgrif.workflow.workflow.service;

import com.netgrif.workflow.auth.domain.Authority;
import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.auth.domain.repositories.UserRepository;
import com.netgrif.workflow.auth.service.interfaces.IAuthorityService;
import com.netgrif.workflow.importer.service.Config;
import com.netgrif.workflow.importer.service.Importer;
import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.repositories.PetriNetRepository;
import com.netgrif.workflow.petrinet.domain.throwable.TransitionNotExecutableException;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.Task;
import com.netgrif.workflow.workflow.domain.repositories.CaseRepository;
import com.netgrif.workflow.workflow.domain.repositories.TaskRepository;
import com.netgrif.workflow.workflow.service.interfaces.ITaskService;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.util.Collections;

@SpringBootTest
@ActiveProfiles({"test"})
@RunWith(SpringRunner.class)
public class TaskServiceTest {

    @Autowired
    private ITaskService service;

    @Autowired
    private TaskRepository repository;

    @Autowired
    private Importer importer;

    @Autowired
    private IWorkflowService workflowService;

    @Autowired
    private PetriNetRepository petriNetRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IAuthorityService authorityService;

    @Before
    public void setUp() {
        mongoTemplate.getDb().dropDatabase();
        taskRepository.deleteAll();

        importer.importPetriNet(new File("src/test/resources/prikladFM.xml"), "fm net", "fm", new Config());
        PetriNet net = petriNetRepository.findAll().get(0);
        workflowService.createCase(net.getStringId(), "Storage Unit", "color", mockLoggedUser());
    }

    @Test
    public void createTasks() throws Exception {
        Case useCase = caseRepository.findAll().get(0);

        service.createTasks(useCase);

        assert repository.findAll().size() > 0;
    }

    @Test
    public void resetArcTest() throws TransitionNotExecutableException {
        PetriNet net = importer.importPetriNet(new File("src/test/resources/reset_inhibitor_test.xml"), "reset", "rst", new Config()).get();
        LoggedUser loggedUser = mockLoggedUser();
        Case useCase = workflowService.createCase(net.getStringId(), "Reset test", "color", loggedUser);
        User user = new User();
        user.setName("name");
        user.setPassword("password");
        user.setSurname("surname");
        user.setEmail("email@email.com");
        user = userRepository.save(user);

        assert useCase.getResetArcTokens().size() == 0;
        assert useCase.getActivePlaces().size() == 1;
        assert useCase.getActivePlaces().values().contains(5);

        Task task = taskRepository.findAll().stream().filter(t -> t.getTitle().getDefaultValue().equalsIgnoreCase("reset")).findFirst().orElse(null);

        service.assignTask(loggedUser, task.getStringId());
        useCase = caseRepository.findOne(useCase.getStringId());

        assert useCase.getResetArcTokens().size() == 1;
        assert useCase.getResetArcTokens().values().contains(5);
        assert useCase.getActivePlaces().size() == 0;

        service.cancelTask(loggedUser, task.getStringId());
        useCase = caseRepository.findOne(useCase.getStringId());

        assert useCase.getResetArcTokens().size() == 0;
        assert useCase.getActivePlaces().size() == 1;
        assert useCase.getActivePlaces().values().contains(5);
    }

    public LoggedUser mockLoggedUser(){
        Authority authorityUser = authorityService.getOrCreate(Authority.user);
        return new LoggedUser(1L, "super@netgrif.com","password", Collections.singleton(authorityUser));
    }
}