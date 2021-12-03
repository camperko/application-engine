package com.netgrif.workflow.elastic

import com.netgrif.workflow.MockService
import com.netgrif.workflow.auth.service.interfaces.IUserService
import com.netgrif.workflow.elastic.domain.ElasticCase
import com.netgrif.workflow.elastic.domain.ElasticCaseRepository
import com.netgrif.workflow.elastic.domain.ElasticTask
import com.netgrif.workflow.elastic.service.interfaces.IElasticCaseService
import com.netgrif.workflow.elastic.web.requestbodies.CaseSearchRequest
import com.netgrif.workflow.petrinet.domain.dataset.ChoiceField
import com.netgrif.workflow.petrinet.domain.dataset.FileFieldValue
import com.netgrif.workflow.petrinet.domain.dataset.FileListFieldValue
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService
import com.netgrif.workflow.startup.ImportHelper
import com.netgrif.workflow.startup.SuperCreator
import com.netgrif.workflow.workflow.domain.Case
import com.netgrif.workflow.workflow.domain.QTask
import com.netgrif.workflow.workflow.domain.Task
import com.netgrif.workflow.workflow.service.interfaces.IDataService
import com.netgrif.workflow.workflow.service.interfaces.ITaskService
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.context.WebApplicationContext

import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@SpringBootTest()
@ActiveProfiles(["test"])
@ExtendWith(SpringExtension.class)
@Disabled("UnsatisfiedDependency Error creating")
class DataSearchRequestTest {

    private static final Logger log = LoggerFactory.getLogger(DataSearchRequestTest)

    public static final String PROCESS_TITLE = "Elastic data search request test"
    public static final String PROCESS_INITIALS = "TST"

    @Autowired
    private WebApplicationContext wac

    @Autowired
    private ImportHelper importHelper

    @Autowired
    private ElasticCaseRepository repository

    @Autowired
    private ElasticsearchTemplate template

    @Autowired
    private IWorkflowService workflowService

    @Autowired
    private IUserService userService

    @Autowired
    private MockService mockService

    @Autowired
    private IElasticCaseService searchService

    @Autowired
    private IPetriNetService petriNetService

    @Autowired
    private SuperCreator superCreator

    @Autowired
    private ITaskService taskService

    @Autowired
    private IDataService dataService

    private ArrayList<Map.Entry<String, String>> testCases

    @BeforeEach
    void before() {
        template.deleteIndex(ElasticCase.class)
        template.createIndex(ElasticCase.class)
        template.putMapping(ElasticCase.class)

        template.deleteIndex(ElasticTask.class)
        template.createIndex(ElasticTask.class)
        template.putMapping(ElasticTask.class)

        repository.deleteAll()

        def net = petriNetService.importPetriNet(new FileInputStream("src/test/resources/all_data.xml"), "major", superCreator.getLoggedSuper())
        assert net.getNet() != null

        def users = userService.findAll(true)
        assert users.size() >= 2
        def testUser1 = users[0]
        def testUser2 = users[1]
        // saving authorities / roles crashes the workflowService (on case save)
        testUser1.processRoles = []
        testUser1.authorities = []
        testUser2.processRoles = []
        testUser2.authorities = []

        LocalDate date = LocalDate.of(2020, 7, 25);
        Case _case = importHelper.createCase("correct", net.getNet())
        _case.dataSet["number"].value = 7.0 as Double
        _case.dataSet["boolean"].value = true
        _case.dataSet["text"].value = "hello world" as String
        _case.dataSet["user"].value = testUser1
        _case.dataSet["date"].value = date
        _case.dataSet["datetime"].value = date.atTime(13, 37)
        _case.dataSet["enumeration"].value = (_case.petriNet.dataSet["enumeration"] as ChoiceField).choices.find({ it.defaultValue == "Alice" })
        _case.dataSet["multichoice"].value = (_case.petriNet.dataSet["multichoice"] as ChoiceField).choices.findAll({ it.defaultValue == "Alice" || it.defaultValue == "Bob" }).toSet()
        _case.dataSet["enumeration_map"].value = "alice"
        _case.dataSet["multichoice_map"].value = ["alice", "bob"].toSet()
        _case.dataSet["file"].value = FileFieldValue.fromString("singlefile.txt")
        _case.dataSet["fileList"].value = FileListFieldValue.fromString("multifile1.txt,multifile2.pdf")
        _case.dataSet["userList"].value = [testUser1.id, testUser2.id]
        workflowService.save(_case)

        Task actionTrigger = taskService.searchOne(QTask.task.caseId.eq(_case.stringId).and(QTask.task.transitionId.eq("2")));
        assert actionTrigger != null
        dataService.setData(actionTrigger, ImportHelper.populateDataset(["testActionTrigger": ["value": "random value", "type": "text"]]))

        10.times {
            _case = importHelper.createCase("wrong${it}", net.getNet())
            workflowService.save(_case)
        }

        testCases = [
                new AbstractMap.SimpleEntry<String, String>("number" as String, "7.0" as String),
                new AbstractMap.SimpleEntry<String, String>("number.numberValue" as String, "7" as String),
                new AbstractMap.SimpleEntry<String, String>("boolean" as String, "true" as String),
                new AbstractMap.SimpleEntry<String, String>("boolean.booleanValue" as String, "true" as String),
                new AbstractMap.SimpleEntry<String, String>("text" as String, "hello world" as String),
                new AbstractMap.SimpleEntry<String, String>("text.textValue.keyword" as String, "hello world" as String),
                new AbstractMap.SimpleEntry<String, String>("user" as String, "${testUser1.fullName} ${testUser1.email}" as String),
                new AbstractMap.SimpleEntry<String, String>("user.emailValue.keyword" as String, "${testUser1.email}" as String),
                new AbstractMap.SimpleEntry<String, String>("user.fullNameValue.keyword" as String, "${testUser1.fullName}" as String),
                new AbstractMap.SimpleEntry<String, String>("user.userIdValue" as String, "${testUser1.getId()}" as String),
                new AbstractMap.SimpleEntry<String, String>("date.timestampValue" as String, "${Timestamp.valueOf(LocalDateTime.of(date, LocalTime.NOON)).getTime()}" as String),
                new AbstractMap.SimpleEntry<String, String>("datetime.timestampValue" as String, "${Timestamp.valueOf(date.atTime(13, 37)).getTime()}" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration" as String, "Alice" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration" as String, "Alica" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration.textValue.keyword" as String, "Alice" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration.textValue.keyword" as String, "Alica" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice" as String, "Alice" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice" as String, "Alica" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice" as String, "Bob" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice" as String, "Bobek" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice.textValue.keyword" as String, "Alice" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice.textValue.keyword" as String, "Alica" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice.textValue.keyword" as String, "Bob" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice.textValue.keyword" as String, "Bobek" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration_map" as String, "Alice" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration_map" as String, "Alica" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration_map.textValue.keyword" as String, "Alice" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration_map.textValue.keyword" as String, "Alica" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration_map.keyValue" as String, "alice" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map" as String, "Alice" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map" as String, "Alica" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map" as String, "Bob" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map" as String, "Bobek" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map.textValue.keyword" as String, "Alice" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map.textValue.keyword" as String, "Alica" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map.textValue.keyword" as String, "Bob" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map.textValue.keyword" as String, "Bobek" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map.keyValue" as String, "alice" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map.keyValue" as String, "bob" as String),
                new AbstractMap.SimpleEntry<String, String>("file" as String, "singlefile.txt" as String),
                new AbstractMap.SimpleEntry<String, String>("file.fileNameValue.keyword" as String, "singlefile" as String),
                new AbstractMap.SimpleEntry<String, String>("file.fileExtensionValue.keyword" as String, "txt" as String),
                new AbstractMap.SimpleEntry<String, String>("fileList" as String, "multifile1.txt" as String),
                new AbstractMap.SimpleEntry<String, String>("fileList" as String, "multifile2.pdf" as String),
                new AbstractMap.SimpleEntry<String, String>("fileList.fileNameValue.keyword" as String, "multifile1" as String),
                new AbstractMap.SimpleEntry<String, String>("fileList.fileNameValue.keyword" as String, "multifile2" as String),
                new AbstractMap.SimpleEntry<String, String>("fileList.fileExtensionValue.keyword" as String, "txt" as String),
                new AbstractMap.SimpleEntry<String, String>("fileList.fileExtensionValue.keyword" as String, "pdf" as String),
                new AbstractMap.SimpleEntry<String, String>("userList" as String, "${testUser1.fullName} ${testUser1.email}" as String),
                new AbstractMap.SimpleEntry<String, String>("userList" as String, "${testUser2.fullName} ${testUser2.email}" as String),
                new AbstractMap.SimpleEntry<String, String>("userList.emailValue.keyword" as String, "${testUser1.email}" as String),
                new AbstractMap.SimpleEntry<String, String>("userList.emailValue.keyword" as String, "${testUser2.email}" as String),
                new AbstractMap.SimpleEntry<String, String>("userList.fullNameValue.keyword" as String, "${testUser1.fullName}" as String),
                new AbstractMap.SimpleEntry<String, String>("userList.fullNameValue.keyword" as String, "${testUser2.fullName}" as String),
                new AbstractMap.SimpleEntry<String, String>("userList.userIdValue" as String, "${testUser1.getId()}" as String),
                new AbstractMap.SimpleEntry<String, String>("userList.userIdValue" as String, "${testUser2.getId()}" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration_map_changed" as String, "Eve" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration_map_changed" as String, "Eva" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration_map_changed.textValue.keyword" as String, "Eve" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration_map_changed.textValue.keyword" as String, "Eva" as String),
                new AbstractMap.SimpleEntry<String, String>("enumeration_map_changed.keyValue" as String, "eve" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map_changed" as String, "Eve" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map_changed" as String, "Eva" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map_changed" as String, "Felix" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map_changed" as String, "Félix" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map_changed.textValue.keyword" as String, "Eve" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map_changed.textValue.keyword" as String, "Eva" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map_changed.textValue.keyword" as String, "Felix" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map_changed.textValue.keyword" as String, "Félix" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map_changed.keyValue" as String, "eve" as String),
                new AbstractMap.SimpleEntry<String, String>("multichoice_map_changed.keyValue" as String, "felix" as String),
        ]
    }

    @Test
    void testDatSearchRequests() {
        testCases.each { testCase ->
            CaseSearchRequest request = new CaseSearchRequest()
            request.data = new HashMap<>()
            request.data.put(testCase.getKey(), testCase.getValue())

            log.info(String.format("Testing %s == %s", testCase.getKey(), testCase.getValue()))

            Page<Case> result = searchService.search([request] as List, mockService.mockLoggedUser(), PageRequest.of(0, 100), null, false)
            assert result.size() == 1
        }
    }
}
