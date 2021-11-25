package com.netgrif.workflow.rules.service;

import com.netgrif.workflow.TestHelper;
import com.netgrif.workflow.WorkflowManagementSystemApplication;
import com.netgrif.workflow.auth.domain.IUser;
import com.netgrif.workflow.configuration.drools.RefreshableKieBase;
import com.netgrif.workflow.importer.service.throwable.MissingIconKeyException;
import com.netgrif.workflow.petrinet.domain.throwable.MissingPetriNetMetaDataException;
import com.netgrif.workflow.petrinet.domain.throwable.TransitionNotExecutableException;
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService;
import com.netgrif.workflow.rules.domain.FactRepository;
import com.netgrif.workflow.rules.domain.RuleRepository;
import com.netgrif.workflow.rules.domain.StoredRule;
import com.netgrif.workflow.rules.domain.facts.*;
import com.netgrif.workflow.startup.SuperCreator;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.Task;
import com.netgrif.workflow.workflow.domain.eventoutcomes.caseoutcomes.CreateCaseEventOutcome;
import com.netgrif.workflow.workflow.domain.eventoutcomes.petrinetoutcomes.ImportPetriNetEventOutcome;
import com.netgrif.workflow.workflow.service.interfaces.ITaskService;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = WorkflowManagementSystemApplication.class
)
@TestPropertySource(
        locations = "classpath:application-test.properties"
)
@ActiveProfiles({"test"})
@ExtendWith(SpringExtension.class)
public class RuleEngineTest {

    @Autowired
    private TestHelper testHelper;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private RefreshableKieBase refreshableKieBase;

    @Autowired
    private IWorkflowService workflowService;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private IPetriNetService petriNetService;

    @Autowired
    private FactRepository factRepository;

    @Autowired
    private SuperCreator superCreator;

    @BeforeEach
    public void before() {
        testHelper.truncateDbs();
    }

    @AfterEach
    public void after() {
        ruleRepository.deleteAll();
        factRepository.deleteAll();
    }

    @Test
    public void testNetImportRulePRE() throws IOException, MissingPetriNetMetaDataException, MissingIconKeyException {
        final String NET_TITLE_PRE = "PRE_TITLE";
        final String TEST_FIELD = "TEST_FIELD";

        StoredRule rule = StoredRule.builder()
                .when("$net: PetriNet() $event: NetImportedFact(netId == $net.stringId, eventPhase == com.netgrif.workflow.petrinet.domain.events.EventPhase.PRE)")
                .then("$net.title.defaultValue = \"" + NET_TITLE_PRE + "\"; \n" +
                        "$net.dataSet.put(\"" + TEST_FIELD + "\", new com.netgrif.workflow.petrinet.domain.dataset.TextField()); \n" +
                        "factRepository.save($event)")
                .identifier("rule1")
                .lastUpdate(LocalDateTime.now())
                .enabled(true)
                .build();
        ruleRepository.save(rule);

        ImportPetriNetEventOutcome outcome = petriNetService.importPetriNet(new FileInputStream("src/test/resources/rule_engine_test.xml"), "major", superCreator.getLoggedSuper());

        assert outcome.getNet() != null;
        assert outcome.getNet().getTitle().getDefaultValue().equals(NET_TITLE_PRE);
        assert outcome.getNet().getDataSet().containsKey(TEST_FIELD);
        assert outcome.getNet().getDataSet().get(TEST_FIELD) != null;

        List<Fact> facts = factRepository.findAll(QNetImportedFact.netImportedFact.netId.eq(outcome.getNet().getStringId()), PageRequest.of(0, 100)).getContent();
        assert facts.size() == 1 && facts.get(0) instanceof NetImportedFact;
    }

    @Test
    public void testNetImportRulePOST() throws IOException, MissingPetriNetMetaDataException, MissingIconKeyException {
        final String NET_TITLE_POST = "POST_TITLE";
        final String NEW_INITIALS = "PST";

        StoredRule rule = StoredRule.builder()
                .when("$net: PetriNet() $event: NetImportedFact(netId == $net.stringId, eventPhase == com.netgrif.workflow.petrinet.domain.events.EventPhase.PRE)")
                .then("$net.initials = \"" + NEW_INITIALS + "\"; \n" +
                        "factRepository.save($event)")
                .identifier("rule1")
                .lastUpdate(LocalDateTime.now())
                .enabled(true)
                .build();
        ruleRepository.save(rule);

        StoredRule rule2 = StoredRule.builder()
                .when("$net: PetriNet() $event: NetImportedFact(netId == $net.stringId, eventPhase == com.netgrif.workflow.petrinet.domain.events.EventPhase.POST)")
                .then("$net.title.defaultValue = \"" + NET_TITLE_POST + "\"; \n factRepository.save($event)")
                .identifier("rule2")
                .lastUpdate(LocalDateTime.now())
                .enabled(true)
                .build();
        ruleRepository.save(rule2);

        assert refreshableKieBase.shouldRefresh();

        ImportPetriNetEventOutcome outcome = petriNetService.importPetriNet(new FileInputStream("src/test/resources/rule_engine_test.xml"), "major", superCreator.getLoggedSuper());

        assert !refreshableKieBase.shouldRefresh();

        assert outcome.getNet() != null;
        assert outcome.getNet().getTitle().getDefaultValue().equals(NET_TITLE_POST);
        assert outcome.getNet().getInitials().equals(NEW_INITIALS);

        ruleRepository.deleteAll();
        factRepository.deleteAll();
        refreshableKieBase.shouldRefresh();
    }

    @Test
    public void testTransitionRules() throws IOException, MissingPetriNetMetaDataException, TransitionNotExecutableException, MissingIconKeyException {
        final String TRANS_1 = "2";
        final String TRANS_2 = "4";
        final String NEW_CASE_TITLE = "new case title";
        final String NEW_CASE_TITLE_2 = "new case title 2";
        final String TEXT_VALUE = "TEXT FIELD VALUE";

        ImportPetriNetEventOutcome outcome = petriNetService.importPetriNet(new FileInputStream("src/test/resources/rule_engine_test.xml"), "major", superCreator.getLoggedSuper());
        assert outcome != null;

        StoredRule rule = StoredRule.builder()
                ._id(new ObjectId())
                .when("$case: Case() $event: CaseCreatedFact(caseId == $case.stringId, eventPhase == com.netgrif.workflow.petrinet.domain.events.EventPhase.POST)")
                .then("$case.title = \"" + NEW_CASE_TITLE + "\";    \n log.info(\"rule 1 matched\");")
                .identifier("rule1")
                .lastUpdate(LocalDateTime.now())
                .enabled(true)
                .build();
        StoredRule rule2 = StoredRule.builder()
                ._id(new ObjectId())
                .when("$case: Case() $event: TransitionEventFact(caseId == $case.stringId, transitionId == \"" + TRANS_1 + "\", type == com.netgrif.workflow.petrinet.domain.events.EventType.FINISH, phase == com.netgrif.workflow.petrinet.domain.events.EventPhase.POST)")
                .then("insert(com.netgrif.workflow.rules.service.RuleEngineTest.TestFact.instance($case.stringId, 0));    \n $case.dataSet[\"text_data\"].value = \"" + TEXT_VALUE + "\";    \n log.info(\"rule 2 matched\");")
                .identifier("rule2")
                .lastUpdate(LocalDateTime.now())
                .enabled(true)
                .build();
        StoredRule rule3 = StoredRule.builder()
                ._id(new ObjectId())
                .when("$testFact: com.netgrif.workflow.rules.service.RuleEngineTest.TestFact()")
                .then("$testFact.increment();     \n factRepository.save($testFact)    \n log.info(\"rule 3 matched\");")
                .identifier("rule3")
                .lastUpdate(LocalDateTime.now())
                .enabled(true)
                .build();
        StoredRule rule4 = StoredRule.builder()
                ._id(new ObjectId())
                .when("$case: Case([\"" + TEXT_VALUE + "\"] contains dataSet[\"text_data\"].value) $event: TransitionEventFact(caseId == $case.stringId, transitionId == \"" + TRANS_2 + "\", type == com.netgrif.workflow.petrinet.domain.events.EventType.FINISH, phase == com.netgrif.workflow.petrinet.domain.events.EventPhase.POST)")
                .then("$case.title = \"" + NEW_CASE_TITLE_2 + "\";    \n log.info(\"rule 4 matched\");")
                .identifier("rule4")
                .lastUpdate(LocalDateTime.now())
                .enabled(true)
                .build();
        ruleRepository.save(rule);
        ruleRepository.save(rule2);
        ruleRepository.save(rule3);
        ruleRepository.save(rule4);

        CreateCaseEventOutcome caseOutcome = workflowService.createCase(outcome.getNet().getStringId(), "Original title", "original color", superCreator.getLoggedSuper());
        assert caseOutcome.getCase().getTitle().equals(NEW_CASE_TITLE);

        Task task = findTask(caseOutcome.getCase(), TRANS_1);
        taskService.assignTask(task, superCreator.getLoggedSuper().transformToUser());
        taskService.finishTask(task, superCreator.getLoggedSuper().transformToUser());
        Case newCase = workflowService.findOne(caseOutcome.getCase().getStringId());
        assert newCase.getTitle().equals(NEW_CASE_TITLE);
        assert !newCase.getColor().equals(NEW_CASE_TITLE_2);

        List<Fact> facts = factRepository.findAll(QCaseFact.caseFact.caseId.eq(newCase.getStringId()), PageRequest.of(0, 100)).getContent();

        assert facts.size() == 1 && facts.get(0) instanceof TestFact && ((TestFact) facts.get(0)).number == 1;

        Task task2 = findTask(newCase, TRANS_2);
        taskService.assignTask(task2, superCreator.getLoggedSuper().transformToUser());
        taskService.finishTask(task2, superCreator.getLoggedSuper().transformToUser());
        newCase = workflowService.findOne(newCase.getStringId());

        assert newCase.getTitle().equals(NEW_CASE_TITLE_2);
    }


    public static final String TEXT_VALUE = "new text value";
    public static final Double NUM_VALUE = 99.0;
    public static final String TRANS_1 = "2";

    @Test
    public void testAssign() throws IOException, MissingPetriNetMetaDataException, TransitionNotExecutableException, MissingIconKeyException {
        StoredRule rule = transitionRulePre(TRANS_1, "com.netgrif.workflow.petrinet.domain.events.EventType.ASSIGN");
        StoredRule rule2 = transitionRulePost(TRANS_1, "com.netgrif.workflow.petrinet.domain.events.EventType.ASSIGN");

        ruleRepository.save(rule);
        ruleRepository.save(rule2);

        Case caze = newCase();
        Task task = findTask(caze, TRANS_1);
        taskService.assignTask(task, superCreator.getLoggedSuper().transformToUser());
        caze = workflowService.findOne(caze.getStringId());

        assert caze.getDataSet().get("text_data").getValue().equals(TEXT_VALUE);
        assert caze.getDataSet().get("number_data").getValue().equals(NUM_VALUE);
    }

    @Test
    public void testDelegate() throws IOException, MissingPetriNetMetaDataException, TransitionNotExecutableException, MissingIconKeyException {
        StoredRule rule = transitionRulePre(TRANS_1, "EventType.DELEGATE");
        StoredRule rule2 = transitionRulePost(TRANS_1, "EventType.DELEGATE");

        ruleRepository.save(rule);
        ruleRepository.save(rule2);

        Case caze = newCase();
        Task task = findTask(caze, TRANS_1);
        IUser user = superCreator.getLoggedSuper().transformToUser();
        taskService.assignTask(task, user);
        taskService.delegateTask(user.transformToLoggedUser(), user.getStringId(), task.getStringId());
        caze = workflowService.findOne(caze.getStringId());

        assert caze.getDataSet().get("text_data").getValue().equals(TEXT_VALUE);
        assert caze.getDataSet().get("number_data").getValue().equals(NUM_VALUE);
    }

    @Test
    public void testFinish() throws IOException, MissingPetriNetMetaDataException, TransitionNotExecutableException, MissingIconKeyException {
        StoredRule rule = transitionRulePre(TRANS_1, "EventType.FINISH");
        StoredRule rule2 = transitionRulePost(TRANS_1, "EventType.FINISH");

        ruleRepository.save(rule);
        ruleRepository.save(rule2);

        Case caze = newCase();
        Task task = findTask(caze, TRANS_1);
        IUser user = superCreator.getLoggedSuper().transformToUser();
        taskService.assignTask(task, user);
        taskService.finishTask(task, user);
        caze = workflowService.findOne(caze.getStringId());

        assert caze.getDataSet().get("text_data").getValue().equals(TEXT_VALUE);
        assert caze.getDataSet().get("number_data").getValue().equals(NUM_VALUE);
    }

    @Test
    public void testCancel() throws IOException, MissingPetriNetMetaDataException, TransitionNotExecutableException, MissingIconKeyException {
        StoredRule rule = transitionRulePre(TRANS_1, "EventType.CANCEL");
        StoredRule rule2 = transitionRulePost(TRANS_1, "EventType.CANCEL");

        ruleRepository.save(rule);
        ruleRepository.save(rule2);

        Case caze = newCase();
        Task task = findTask(caze, TRANS_1);
        IUser user = superCreator.getLoggedSuper().transformToUser();

        taskService.assignTask(task, user);
        taskService.cancelTask(task, user);
        caze = workflowService.findOne(caze.getStringId());

        assert caze.getDataSet().get("text_data").getValue().equals(TEXT_VALUE);
        assert caze.getDataSet().get("number_data").getValue().equals(NUM_VALUE);
    }

    @Test
    public void testQueries() throws IOException, MissingPetriNetMetaDataException, MissingIconKeyException {
        String predicate = "$event: CaseCreatedFact(eventPhase == com.netgrif.workflow.petrinet.domain.events.EventPhase.POST)";
        String then = "factRepository.save(com.netgrif.workflow.rules.service.RuleEngineTest.TestFact.instance($case.stringId, %d));";
        StoredRule rule0 = rule(predicate + " $case: Case(processIdentifier == \"rule_engine_test\", title == \"FAKE_TITLE\")", String.format(then, -2));
        StoredRule rule1 = rule(predicate + " $case: Case(processIdentifier == \"FAKE_NET\")", String.format(then, -1));
        StoredRule rule2 = rule(predicate + " $case: Case(processIdentifier == \"rule_engine_test\")", String.format(then, 1));
        StoredRule rule3 = rule(predicate + " $case: Case(processIdentifier == \"rule_engine_test\", dataSet[\"text_data\"].value == \"VALUE\")", String.format(then, 2));

        ruleRepository.save(rule0);
        ruleRepository.save(rule1);
        ruleRepository.save(rule2);
        ruleRepository.save(rule3);

        Case caze = newCase();
        List<Fact> facts = factRepository.findAll(QCaseFact.caseFact.caseId.eq(caze.getStringId()), PageRequest.of(0, 100)).getContent();
        assert facts.stream().noneMatch(it -> ((TestFact) it).number == -2);
        assert facts.stream().noneMatch(it -> ((TestFact) it).number == -1);
        assert facts.stream().filter(it -> ((TestFact) it).number == 1).count() == 1;
        assert facts.stream().filter(it -> ((TestFact) it).number == 2).count() == 1;

    }

    @Test
    @Disabled("TODO:")
    public void stressTest() throws IOException, MissingPetriNetMetaDataException, MissingIconKeyException {
        StoredRule rule = rule("$case: Case() \n $event: CaseCreatedFact(caseId == $case.stringId, eventPhase == com.netgrif.workflow.petrinet.domain.events.EventPhase.POST)", "log.info($case.stringId)");
        IntStream.range(0, 10000).forEach(number -> {
            rule.set_id(new ObjectId());
            ruleRepository.save(rule);
        });
        StoredRule rule2 = rule("$case: Case() \n $event: CaseCreatedFact(caseId == $case.stringId, eventPhase == com.netgrif.workflow.petrinet.domain.events.EventPhase.POST)", "$case.title = \"NEW_TITLE\"");
        ruleRepository.save(rule2);

        Case caze = newCase();
        assert caze.getTitle().equals("NEW_TITLE");
    }

    private StoredRule transitionRulePre(String trans, String type) {
        String when = "$case: Case() $event: TransitionEventFact(caseId == $case.stringId, transitionId == \"" + trans + "\", type == " + type + ", phase == com.netgrif.workflow.petrinet.domain.events.EventPhase.PRE)";
        String then = "$case.dataSet[\"text_data\"].value = \"" + TEXT_VALUE + "\";";

        return rule(when, then);
    }

    private StoredRule transitionRulePost(String trans, String type) {
        String when = "$case: Case() $event: TransitionEventFact(caseId == $case.stringId, transitionId == \"" + trans + "\", type == " + type + ", phase == com.netgrif.workflow.petrinet.domain.events.EventPhase.POST)";
        String then = "$case.dataSet[\"number_data\"].value = " + NUM_VALUE + ";";

        return rule(when, then);
    }

    private StoredRule rule(String when, String then) {
        ObjectId id = new ObjectId();
        return StoredRule.builder()
                ._id(id)
                .when(when)
                .then(then)
                .identifier(id.toString())
                .lastUpdate(LocalDateTime.now())
                .enabled(true)
                .build();
    }

    private Case newCase() throws IOException, MissingPetriNetMetaDataException, MissingIconKeyException {
        ImportPetriNetEventOutcome outcome = petriNetService.importPetriNet(new FileInputStream("src/test/resources/rule_engine_test.xml"), "major", superCreator.getLoggedSuper());
        return workflowService.createCase(outcome.getNet().getStringId(), "Original title", "original color", superCreator.getLoggedSuper()).getCase();
    }

    private Task findTask(Case caze, String trans) {
        return taskService.findOne(caze.getTasks().stream().filter(it -> it.getTransition().equals(trans)).findFirst().get().getTask());
    }

    public static class TestFact extends CaseFact {

        private Integer number;

        public TestFact(String caseId, Integer number) {
            super(caseId);
            this.number = number;
        }

        public Integer increment() {
            return ++number;
        }

        public static TestFact instance(String caseId, Integer number) {
            return new TestFact(caseId, number);
        }
    }


}
