package com.netgrif.workflow.auth

import com.netgrif.workflow.auth.domain.Authority
import com.netgrif.workflow.auth.domain.User
import com.netgrif.workflow.auth.domain.UserState
import com.netgrif.workflow.importer.service.Importer
import com.netgrif.workflow.petrinet.domain.PetriNet
import com.netgrif.workflow.petrinet.domain.VersionType
import com.netgrif.workflow.petrinet.domain.roles.ProcessRole
import com.netgrif.workflow.petrinet.domain.roles.ProcessRoleRepository
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService
import com.netgrif.workflow.startup.ImportHelper
import com.netgrif.workflow.startup.SuperCreator
import groovy.json.JsonOutput

//import com.netgrif.workflow.orgstructure.domain.Group

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ExtendWith(SpringExtension.class)
@ActiveProfiles(["test"])
@SpringBootTest
class TaskAuthorizationServiceTest {

    private static final String ASSIGN_TASK_URL = "/api/task/assign/"
    private static final String DELEGATE_TASK_URL = "/api/task/delegate/"
    private static final String FINISH_TASK_URL = "/api/task/finish/"
    private static final String CANCEL_TASK_URL = "/api/task/cancel/"
    private static final String SET_DATA_URL_TEMPLATE = "/api/task/%s/data"
    private static final String SET_FILE_URL_TEMPLATE = "/api/task/%s/file/%s"

    private static final String USER_WITH_ROLE_EMAIL = "role@test.com"
    private static final String USER_WITHOUT_ROLE_EMAIL = "norole@test.com"
    private static final String ADMIN_USER_EMAIL = "admin@test.com"

    private MockMvc mvc

    private PetriNet net

    private String userId

    private Authentication userWithRoleAuth
    private Authentication userWithoutRoleAuth
    private Authentication adminAuth


    @Autowired
    private Importer importer

    @Autowired
    private WebApplicationContext wac

    @Autowired
    private ImportHelper importHelper

    @Autowired
    private IPetriNetService petriNetService

    @Autowired
    private ProcessRoleRepository userProcessRoleRepository


    @Autowired
    private SuperCreator superCreator

    @BeforeEach
    void before() {
        def net = petriNetService.importPetriNet(new FileInputStream("src/test/resources/task_authentication_service_test.xml"), VersionType.MAJOR, superCreator.getLoggedSuper())
        assert net.getNet() != null

        this.net = net.getNet()

        mvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .apply(springSecurity())
                .build()

        def auths = importHelper.createAuthorities(["user": Authority.user, "admin": Authority.admin])
        def processRoles = userProcessRoleRepository.findAllByNetId(this.net.getStringId())

        def user = importHelper.createUser(new User(name: "Role", surname: "User", email: USER_WITH_ROLE_EMAIL, password: "password", state: UserState.ACTIVE),
                [auths.get("user")] as Authority[],
                [processRoles.find({it.name.equals("role")})] as ProcessRole[])

        userId = user.getStringId()
        this.userWithRoleAuth = new UsernamePasswordAuthenticationToken(USER_WITH_ROLE_EMAIL, "password")

        importHelper.createUser(new User(name: "NoRole", surname: "User", email: USER_WITHOUT_ROLE_EMAIL, password: "password", state: UserState.ACTIVE),
                [auths.get("user")] as Authority[],
                [] as ProcessRole[])

        this.userWithoutRoleAuth = new UsernamePasswordAuthenticationToken(USER_WITHOUT_ROLE_EMAIL, "password")

        importHelper.createUser(new User(name: "Admin", surname: "User", email: ADMIN_USER_EMAIL, password: "password", state: UserState.ACTIVE),
                [auths.get("admin")] as Authority[],
                [] as ProcessRole[])

        this.adminAuth = new UsernamePasswordAuthenticationToken(ADMIN_USER_EMAIL, "password")
    }


    void beforeEach() {
        def aCase = importHelper.createCase("Case", this.net)
        assert aCase != null

        taskId = importHelper.getTaskId("Transition", aCase.stringId)
        assert taskId != null

        aCase = importHelper.createCase("Case 2", this.net)
        assert aCase != null

        taskId2 = importHelper.getTaskId("Transition", aCase.stringId)
        assert taskId2 != null
    }

    private String taskId
    private String taskId2

    @Test
    @Disabled("Assign Test")
    void testTaskAuthorizationService() {
        def tests = [
                { -> testAssignAuthorization() },
                { -> testDelegateAuthorization() },
                { -> testFinishAuthorization() },
                { -> testCancelAuthorization() },
                { -> testSetDataAuthorization() },
//                { -> testSetFileAuthorization() },
        ]
        tests.each { t ->
            beforeEach()
            t()
        }
    }

    void testAssignAuthorization() {
        mvc.perform(get(ASSIGN_TASK_URL + taskId)
                .with(authentication(userWithoutRoleAuth)))
                .andExpect(status().is4xxClientError())
        mvc.perform(get(ASSIGN_TASK_URL + taskId)
                .with(authentication(userWithRoleAuth)))
                .andExpect(status().isOk())
        mvc.perform(get(ASSIGN_TASK_URL + taskId2)
                .with(authentication(adminAuth)))
                .andExpect(status().isOk())
    }

    void testDelegateAuthorization() {
        mvc.perform(post(DELEGATE_TASK_URL + taskId)
                .content(userId.toString())
                .contentType(MediaType.TEXT_PLAIN_VALUE)
                .with(authentication(this.userWithoutRoleAuth)))
                .andExpect(status().isForbidden())
        mvc.perform(post(DELEGATE_TASK_URL + taskId)
                .content(userId.toString())
                .contentType(MediaType.TEXT_PLAIN_VALUE)
                .with(authentication(this.userWithRoleAuth)))
                .andExpect(status().isOk())
        mvc.perform(post(DELEGATE_TASK_URL + taskId2)
                .content(userId.toString())
                .contentType(MediaType.TEXT_PLAIN_VALUE)
                .with(authentication(this.adminAuth)))
                .andExpect(status().isOk())
    }

    void testFinishAuthorization() {
        mvc.perform(get(ASSIGN_TASK_URL + taskId)
                .with(authentication(this.userWithRoleAuth)))
                .andExpect(status().isOk())
        mvc.perform(get(ASSIGN_TASK_URL + taskId2)
                .with(authentication(this.userWithRoleAuth)))
                .andExpect(status().isOk())

        mvc.perform(get(FINISH_TASK_URL + taskId)
                .with(authentication(this.userWithoutRoleAuth)))
                .andExpect(status().isForbidden())
        mvc.perform(get(FINISH_TASK_URL + taskId)
                .with(authentication(this.userWithRoleAuth)))
                .andExpect(status().isOk())
        mvc.perform(get(FINISH_TASK_URL + taskId2)
                .with(authentication(this.adminAuth)))
                .andExpect(status().isOk())
    }

    void testCancelAuthorization() {
        mvc.perform(get(ASSIGN_TASK_URL + taskId)
                .with(authentication(this.userWithRoleAuth)))
                .andExpect(status().isOk())
        mvc.perform(get(ASSIGN_TASK_URL + taskId2)
                .with(authentication(this.userWithRoleAuth)))
                .andExpect(status().isOk())

        mvc.perform(get(CANCEL_TASK_URL + taskId)
                .with(authentication(this.userWithoutRoleAuth)))
                .andExpect(status().isForbidden())
        mvc.perform(get(CANCEL_TASK_URL + taskId)
                .with(authentication(this.userWithRoleAuth)))
                .andExpect(status().isOk())
        mvc.perform(get(CANCEL_TASK_URL + taskId2)
                .with(authentication(this.adminAuth)))
                .andExpect(status().isOk())
    }

    void testSetDataAuthorization() {
        mvc.perform(get(ASSIGN_TASK_URL + taskId)
                .with(authentication(this.userWithRoleAuth)))
                .andExpect(status().isOk())
        mvc.perform(get(ASSIGN_TASK_URL + taskId2)
                .with(authentication(this.userWithRoleAuth)))
                .andExpect(status().isOk())

        def body = JsonOutput.toJson([
                text: [
                        value: "Helo world",
                        type : "text"
                ]
        ])

        mvc.perform(post(String.format(SET_DATA_URL_TEMPLATE, taskId))
                .content(body)
                .contentType(APPLICATION_JSON)
                .with(authentication(this.userWithoutRoleAuth)))
                .andExpect(status().isForbidden())
        mvc.perform(post(String.format(SET_DATA_URL_TEMPLATE, taskId))
                .content(body)
                .contentType(APPLICATION_JSON)
                .with(authentication(this.userWithRoleAuth)))
                .andExpect(status().isOk())
        mvc.perform(post(String.format(SET_DATA_URL_TEMPLATE, taskId2))
                .content(body)
                .contentType(APPLICATION_JSON)
                .with(authentication(this.adminAuth)))
                .andExpect(status().isOk())
    }

// TODO 14.8.2020 test for file upload endpoint

//    void testSetFileAuthorization() {
//        mvc.perform(get(ASSIGN_TASK_URL + taskId)
//                .with(authentication(this.userWithRoleAuth)))
//                .andExpect(status().isOk())
//        mvc.perform(get(ASSIGN_TASK_URL + taskId2)
//                .with(authentication(this.userWithRoleAuth)))
//                .andExpect(status().isOk())
//
//        MockMultipartFile file = new MockMultipartFile("data", "filename.txt", "text/plain", "some xml".getBytes());
//
//        mvc.perform(multipart(String.format(SET_FILE_URL_TEMPLATE, taskId, "file"))
//                .file(file)
//                .characterEncoding("UTF-8")
//                .with(authentication(this.userWithoutRoleAuth)))
//                .andExpect(status().isForbidden())
//        mvc.perform(multipart(String.format(SET_FILE_URL_TEMPLATE, taskId, "file"))
//                .file(file)
//                .characterEncoding("UTF-8")
//                .with(authentication(this.userWithRoleAuth)))
//                .andExpect(status().isOk())
//        mvc.perform(multipart(String.format(SET_FILE_URL_TEMPLATE, taskId2, "file"))
//                .file(file)
//                .characterEncoding("UTF-8")
//                .contentType(APPLICATION_JSON)
//                .with(authentication(this.adminAuth)))
//                .andExpect(status().isOk())
//    }
}