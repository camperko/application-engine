package com.netgrif.workflow.petrinet.domain.roles

import com.netgrif.workflow.auth.domain.Authority
import com.netgrif.workflow.auth.domain.User
import com.netgrif.workflow.auth.domain.UserProcessRole
import com.netgrif.workflow.auth.domain.UserState
import com.netgrif.workflow.auth.domain.repositories.UserProcessRoleRepository
import com.netgrif.workflow.importer.service.Importer
import com.netgrif.workflow.orgstructure.domain.Group
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService
import com.netgrif.workflow.startup.ImportHelper
import com.netgrif.workflow.startup.SuperCreator
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.hamcrest.CoreMatchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.hateoas.MediaTypes
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@RunWith(SpringRunner.class)
@ActiveProfiles(["test"])
@SpringBootTest
class ProcessRoleTest {

    private static final String CASE_CREATE_URL = "/api/workflow/case"
    private static final String TASK_SEARCH_URL = "/api/task/search?sort=priority"

    private static final String LOCALE_SK = "sk"

    private static final String CASE_NAME = "Test case"
    private static final String CASE_INITIALS = "TC"
    private static final String USER_EMAIL_VIEW = "ProcessRoleTest@test.com"
    private static final String USER_EMAIL_PERFORM = "ProcessRoleTestPerform@test.com"
    private static final String USER_EMAIL_BOTH = "ProcessRoleTestPerformView@test.com"

    private Authentication auth

    private MockMvc mvc

    @Autowired
    private Importer importer

    @Autowired
    private WebApplicationContext wac

    @Autowired
    private ImportHelper importHelper

    @Autowired
    private IPetriNetService petriNetService;

    @Autowired
    private UserProcessRoleRepository userProcessRoleRepository

    @Autowired
    private SuperCreator superCreator;

    @Before
    void before() {
        mvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .apply(springSecurity())
                .build()

        def net = petriNetService.importPetriNet(new FileInputStream("src/test/resources/rolref_view.xml"), "major", superCreator.getLoggedSuper())
        assert net.isPresent()

        netId = net.get().getStringId()

        def org = importHelper.createGroup("Insurance Company")
        def auths = importHelper.createAuthorities(["user": Authority.user, "admin": Authority.admin])
        def processRoles = userProcessRoleRepository.findAllByNetId(netId)
        importHelper.createUser(new User(name: "Test", surname: "Integration", email: USER_EMAIL_VIEW, password: "password", state: UserState.ACTIVE),
                [auths.get("user")] as Authority[],
                [org] as Group[],
                [processRoles.find { it.roleId == net.get().roles.values().find { it.name.defaultValue == "View" }.stringId }] as UserProcessRole[])

        importHelper.createUser(new User(name: "Test", surname: "Integration", email: USER_EMAIL_PERFORM, password: "password", state: UserState.ACTIVE),
                [auths.get("user")] as Authority[],
                [org] as Group[],
                [processRoles.find { it.roleId == net.get().roles.values().find { it.name.defaultValue == "Perform" }.stringId }] as UserProcessRole[])

        importHelper.createUser(new User(name: "Test", surname: "Integration", email: USER_EMAIL_BOTH, password: "password", state: UserState.ACTIVE),
                [auths.get("user")] as Authority[],
                [org] as Group[],
                [processRoles.find { it.roleId == net.get().roles.values().find { it.name.defaultValue == "View" }.stringId }, processRoles.find { it.roleId == net.get().roles.values().find { it.name.defaultValue == "Perform" }.stringId }] as UserProcessRole[])
    }

    private String caseId
    private String netId
    private String taskId

    @Test
    void testViewLogic() {
        auth = new UsernamePasswordAuthenticationToken(USER_EMAIL_VIEW, "password")
        createCase()
        searchTasks("View", 1)

        auth = new UsernamePasswordAuthenticationToken(USER_EMAIL_PERFORM, "password")
//        createCase()
        searchTasks("Perform", 1)

        auth = new UsernamePasswordAuthenticationToken(USER_EMAIL_BOTH, "password")
//        createCase()
        searchTasks("View", 2)
    }

    def createCase() {
        def content = JsonOutput.toJson([
                title: CASE_NAME,
                netId: netId,
                color: "color"
        ])
        def result = mvc.perform(post(CASE_CREATE_URL)
                .accept(MediaTypes.HAL_JSON_VALUE)
                .content(content)
                .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .with(csrf().asHeader())
                .with(authentication(this.auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.title', CoreMatchers.is(CASE_NAME)))
                .andExpect(jsonPath('$.petriNetId', CoreMatchers.is(netId)))
                .andReturn()
        def response = parseResult(result)
        caseId = response.stringId
    }

    def searchTasks(String title, int expected) {
        def content = JsonOutput.toJson([
                case: [
                        id: caseId
                ]
        ])
        def result = mvc.perform(post(TASK_SEARCH_URL)
                .accept(MediaTypes.HAL_JSON_VALUE)
                .locale(Locale.forLanguageTag(LOCALE_SK))
                .content(content)
                .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .with(csrf().asHeader())
                .with(authentication(this.auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.page.totalElements', CoreMatchers.is(expected)))
                .andReturn()
        def response = parseResult(result)
        taskId = response?._embedded?.tasks?.find { it.title == title }?.stringId
        assert taskId != null
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private def parseResult(MvcResult result) {
        return (new JsonSlurper()).parseText(result.response.contentAsString)
    }
}