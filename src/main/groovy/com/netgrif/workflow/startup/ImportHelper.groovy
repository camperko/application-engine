package com.netgrif.workflow.startup

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.netgrif.workflow.auth.domain.*
import com.netgrif.workflow.auth.domain.repositories.UserProcessRoleRepository
import com.netgrif.workflow.auth.service.interfaces.IAuthorityService
import com.netgrif.workflow.auth.service.interfaces.IUserService
import com.netgrif.workflow.orgstructure.domain.Group
import com.netgrif.workflow.orgstructure.service.IGroupService
import com.netgrif.workflow.orgstructure.service.IMemberService
import com.netgrif.workflow.petrinet.domain.PetriNet
import com.netgrif.workflow.petrinet.domain.dataset.logic.ChangedFieldContainer
import com.netgrif.workflow.petrinet.domain.repositories.PetriNetRepository
import com.netgrif.workflow.petrinet.service.PetriNetService
import com.netgrif.workflow.petrinet.web.requestbodies.UploadedFileMeta
import com.netgrif.workflow.workflow.domain.Case
import com.netgrif.workflow.workflow.domain.Filter
import com.netgrif.workflow.workflow.domain.repositories.CaseRepository
import com.netgrif.workflow.workflow.service.TaskService
import com.netgrif.workflow.workflow.service.interfaces.IFilterService
import com.netgrif.workflow.workflow.web.requestbodies.CreateFilterBody
import com.netgrif.workflow.workflow.web.responsebodies.TaskReference
import groovy.json.JsonOutput
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

import java.time.LocalDateTime

@Component
class ImportHelper {

    public static final String PATCH = "patch"

    public static final String FIELD_BOOLEAN = "boolean"
    public static final String FIELD_ENUMERATION = "enumeration"
    public static final String FIELD_TEXT = "text"
    public static final String FIELD_NUMBER = "number"
    public static final String FIELD_DATE = "date"

    private static final Logger log = Logger.getLogger(ImportHelper.class.name)

    @Autowired
    private PetriNetRepository petriNetRepository

    @Autowired
    private IUserService userService

    @Autowired
    private CaseRepository caseRepository

    @Autowired
    private UserProcessRoleRepository userProcessRoleRepository

    @Autowired
    private IAuthorityService authorityService

    @Autowired
    private TaskService taskService

    @Autowired
    private PetriNetService petriNetService

    @Autowired
    private ResourceLoader resourceLoader

    @Autowired
    private IFilterService filterService

    @Autowired
    private SuperCreator superCreator

    @Autowired
    private IGroupService groupService

    @Autowired
    private IMemberService memberService

    private final ClassLoader loader = ImportHelper.getClassLoader()

    @SuppressWarnings("GroovyAssignabilityCheck")
    Map<String, Group> createGroups(Map<String, String> groups) {
        HashMap<String, Group> groupsMap = new HashMap<>()
        groups.each { groupEntry ->
            groupsMap.put(groupEntry.key, createGroup(groupEntry.value))
        }

        log.info("Created ${groupsMap.size()} groups")
        return groupsMap
    }

    Group createGroup(String name) {
        log.info("Creating Group $name")
        return groupService.save(new Group(name))
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    Map<String, Authority> createAuthorities(Map<String, String> authorities) {
        HashMap<String, Authority> authoritities = new HashMap<>()
        authorities.each { authority ->
            authoritities.put(authority.key, authorityService.getOrCreate(authority.value))
        }

        log.info("Creating ${authoritities.size()} authorities")
        return authoritities
    }

    Authority createAuthority(String name) {
        log.info("Creating authority $name")
        return authorityService.getOrCreate(name)
    }

    Optional<PetriNet> createNet(String fileName, String identifier, String name, String initials, String release) {
        createNet(fileName, identifier, name, initials, release, superCreator.loggedSuper)
    }

    Optional<PetriNet> createNet(String fileName, String identifier, String name, String initials, String release, LoggedUser loggedUser) {
        return petriNetService.importPetriNet(new File("src/main/resources/petriNets/$fileName"),
                new UploadedFileMeta(name, initials, identifier, release), loggedUser)
    }

    UserProcessRole createUserProcessRole(PetriNet net, String name) {
        UserProcessRole role = userProcessRoleRepository.save(new UserProcessRole(roleId:
                net.roles.values().find { it -> it.name.defaultValue == name }.stringId))
        log.info("Created user process role $name")
        return role
    }

    Map<String, UserProcessRole> createUserProcessRoles(Map<String, String> roles, PetriNet net) {
        HashMap<String, UserProcessRole> userRoles = new HashMap<>()
        roles.each { it ->
            userRoles.put(it.key, createUserProcessRole(net, it.value))
        }

        log.info("Created ${userRoles.size()} process roles")
        return userRoles
    }

    Map<String, UserProcessRole> getProcessRoles(PetriNet net) {
        List<UserProcessRole> roles = userProcessRoleRepository.findAllByNetId(net.stringId)
        Map<String, UserProcessRole> map = [:]
        net.roles.values().each { netRole ->
            map[netRole.name.getDefaultValue()] = roles.find { it.roleId == netRole.stringId }
        }
        return map
    }

    User createUser(User user, Authority[] authorities, Group[] orgs, UserProcessRole[] roles) {
        authorities.each { user.addAuthority(it) }
        roles.each { user.addProcessRole(it) }
        user.groups = orgs as Set
        user.state = UserState.ACTIVE
        user = userService.saveNew(user)
        log.info("User $user.name $user.surname created")
        return user
    }

    Case createCase(String title, PetriNet net, LoggedUser user) {
        Case useCase = new Case(title, net, net.getActivePlaces())
        useCase.setProcessIdentifier(net.getIdentifier())
        useCase.setColor(getCaseColor())
        useCase.setAuthor(user.transformToAuthor())
        useCase.setIcon(net.icon)
        useCase.setCreationDate(LocalDateTime.now())
        useCase = caseRepository.save(useCase)
        taskService.createTasks(useCase)
        log.info("Case $title created")
        return useCase
    }

    Case createCase(String title, PetriNet net) {
        return createCase(title, net, superCreator.loggedSuper)
    }

    boolean createFilter(String title, String query, String readable, LoggedUser user) {
        return filterService.saveFilter(new CreateFilterBody(title, Filter.VISIBILITY_PUBLIC, "This filter was created automatically for testing purpose only.", Filter.TYPE_TASK, query, readable), user)
    }

    void assignTask(String taskTitle, String caseId, LoggedUser author) {
        taskService.assignTask(author, getTaskId(taskTitle, caseId))
    }

    void assignTaskToSuper(String taskTitle, String caseId) {
        assignTask(taskTitle, caseId, superCreator.loggedSuper)
    }

    void finishTask(String taskTitle, String caseId, LoggedUser author) {
        taskService.finishTask(author, getTaskId(taskTitle, caseId))
    }

    void finishTaskAsSuper(String taskTitle, String caseId) {
        finishTask(taskTitle, caseId, superCreator.loggedSuper)
    }

    String getTaskId(String taskTitle, String caseId) {
        List<TaskReference> references = taskService.findAllByCase(caseId, null)
        return references.find { it.getTitle() == taskTitle }.stringId
    }

    ChangedFieldContainer setTaskData(String taskId, Map<String, Map<String,String>> data) {
        ObjectNode dataSet = populateDataset(data)
         taskService.setData(taskId, dataSet)
    }

    ChangedFieldContainer setTaskData(String taskTitle, String caseId, Map<String, Map<String,String>> data) {
        setTaskData(getTaskId(taskTitle, caseId), data)
    }

    static ObjectNode populateDataset(Map<String, Map<String, String>> data) {
        ObjectMapper mapper = new ObjectMapper()
        String json = JsonOutput.toJson(data)
        return mapper.readTree(json) as ObjectNode
    }

    static String getCaseColor() {
        return "color-fg-amber-500"
    }

}