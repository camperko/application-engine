package com.netgrif.workflow.petrinet.domain.dataset.logic.action

import com.netgrif.workflow.AsyncRunner
import com.netgrif.workflow.auth.domain.Author
import com.netgrif.workflow.auth.domain.IUser
import com.netgrif.workflow.auth.service.UserDetailsServiceImpl
import com.netgrif.workflow.auth.service.interfaces.IRegistrationService
import com.netgrif.workflow.auth.service.interfaces.IUserService
import com.netgrif.workflow.auth.web.requestbodies.NewUserRequest
import com.netgrif.workflow.configuration.ApplicationContextProvider
import com.netgrif.workflow.workflow.service.interfaces.IUserFilterSearchService
import com.netgrif.workflow.importer.service.FieldFactory
import com.netgrif.workflow.mail.domain.MailDraft
import com.netgrif.workflow.mail.interfaces.IMailAttemptService
import com.netgrif.workflow.mail.interfaces.IMailService
import com.netgrif.workflow.orgstructure.groups.interfaces.INextGroupService
import com.netgrif.workflow.pdf.generator.config.PdfResource
import com.netgrif.workflow.pdf.generator.service.interfaces.IPdfGenerator
import com.netgrif.workflow.petrinet.domain.I18nString
import com.netgrif.workflow.petrinet.domain.PetriNet
import com.netgrif.workflow.petrinet.domain.Transition
import com.netgrif.workflow.petrinet.domain.dataset.*
import com.netgrif.workflow.petrinet.domain.dataset.logic.ChangedField
import com.netgrif.workflow.petrinet.domain.dataset.logic.ChangedFieldsTree
import com.netgrif.workflow.petrinet.domain.dataset.logic.validation.DynamicValidation
import com.netgrif.workflow.petrinet.domain.dataset.logic.validation.Validation
import com.netgrif.workflow.petrinet.domain.version.Version
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService
import com.netgrif.workflow.rules.domain.RuleRepository
import com.netgrif.workflow.startup.ImportHelper
import com.netgrif.workflow.utils.FullPageRequest
import com.netgrif.workflow.workflow.domain.Case
import com.netgrif.workflow.workflow.domain.QCase
import com.netgrif.workflow.workflow.domain.QTask
import com.netgrif.workflow.workflow.domain.Task
import com.netgrif.workflow.workflow.service.TaskService
import com.netgrif.workflow.workflow.service.interfaces.IDataService
import com.netgrif.workflow.workflow.service.interfaces.IDataValidationExpressionEvaluator
import com.netgrif.workflow.workflow.service.interfaces.IInitValueExpressionEvaluator
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService
import com.netgrif.workflow.workflow.web.responsebodies.MessageResource
import com.netgrif.workflow.workflow.web.responsebodies.TaskReference
import com.querydsl.core.types.Predicate
import org.bson.types.ObjectId
import org.quartz.Scheduler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.core.io.ClassPathResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

import java.util.stream.Collectors

/**
 * ActionDelegate class contains Actions API methods.
 */
@SuppressWarnings(["GrMethodMayBeStatic", "GroovyUnusedDeclaration"])
class ActionDelegate {

    static final Logger log = LoggerFactory.getLogger(ActionDelegate)

    static final String UNCHANGED_VALUE = "unchangedooo"
    static final String ALWAYS_GENERATE = "always"
    static final String ONCE_GENERATE = "once"

    @Autowired
    FieldFactory fieldFactory

    @Autowired
    TaskService taskService

    @Autowired
    IDataService dataService

    @Autowired
    IWorkflowService workflowService

    @Autowired
    IUserService userService

    @Autowired
    IPetriNetService petriNetService

    @Autowired
    AsyncRunner async

    @Autowired
    IPdfGenerator pdfGenerator

    @Autowired
    IMailService mailService

    @Autowired
    INextGroupService nextGroupService

    @Autowired
    IRegistrationService registrationService

    @Autowired
    IMailAttemptService mailAttemptService

    @Autowired
    UserDetailsServiceImpl userDetailsService

    @Autowired
    IDataValidationExpressionEvaluator dataValidationExpressionEvaluator

    @Autowired
    IInitValueExpressionEvaluator initValueExpressionEvaluator

    @Autowired
    RuleRepository ruleRepository

    @Autowired
    Scheduler scheduler

    @Autowired
    IUserFilterSearchService filterSearchService

    /**
     * Reference of case and task in which current action is taking place.
     */
    Case useCase
    Optional<Task> task
    def map = [:]
    Action action
    FieldActionsRunner actionsRunner
    ChangedFieldsTree changedFieldsTree

    def init(Action action, Case useCase, Optional<Task> task, FieldActionsRunner actionsRunner) {
        this.action = action
        this.useCase = useCase
        this.task = task
        this.actionsRunner = actionsRunner
        this.initFieldsMap(action.fieldIds)
        this.initTransitionsMap(action.transitionIds)
        changedFieldsTree = ChangedFieldsTree.createNew(useCase ? useCase.stringId : "case",
                task.isPresent() ? task.get().stringId : "task",
                task.isPresent() ? task.get().transitionId : "trans")
    }

    def initFieldsMap(Map<String, String> fieldIds) {
        fieldIds.each { name, id ->
            set(name, fieldFactory.buildFieldWithoutValidation(useCase, id, null))
        }
    }

    def initTransitionsMap(Map<String, String> transitionIds) {
        transitionIds.each { name, id ->
            set(name, useCase.petriNet.transitions[id])
        }
    }

    def copyBehavior(Field field, Transition transition) {
        if (!useCase.hasFieldBehavior(field.stringId, transition.stringId)) {
            useCase.dataSet.get(field.stringId).addBehavior(transition.stringId, transition.dataSet.get(field.stringId).behavior)
        }
    }

    def visible = { Field field, Transition trans ->
        copyBehavior(field, trans)
        useCase.dataSet.get(field.stringId).makeVisible(trans.stringId)
    }

    def editable = { Field field, Transition trans ->
        copyBehavior(field, trans)
        useCase.dataSet.get(field.stringId).makeEditable(trans.stringId)
    }

    def required = { Field field, Transition trans ->
        copyBehavior(field, trans)
        useCase.dataSet.get(field.stringId).makeRequired(trans.stringId)
    }

    def optional = { Field field, Transition trans ->
        copyBehavior(field, trans)
        useCase.dataSet.get(field.stringId).makeOptional(trans.stringId)
    }

    def hidden = { Field field, Transition trans ->
        copyBehavior(field, trans)
        useCase.dataSet.get(field.stringId).makeHidden(trans.stringId)
    }

    def forbidden = { Field field, Transition trans ->
        copyBehavior(field, trans)
        useCase.dataSet.get(field.stringId).makeForbidden(trans.stringId)
    }

    def unchanged = { return UNCHANGED_VALUE }

    def initValueOfField = { Field field ->
        if (!field.hasDefault()) {
            return null
        } else if (field.isDynamicDefaultValue()) {
            return initValueExpressionEvaluator.evaluate(useCase, field)
        }
        return field.defaultValue
    }

    def getInit() {
        return initValueOfField
    }

    def init(Field field) {
        return initValueOfField(field)
    }

    /**
     * Changes behavior of a given field on given transition if certain condition is being met.
     * <br>
     * Example:
     * <pre>
     *     condition: f.conditionId,
     *     text: f.textId,
     *     transition: t.transitionId;
     *
     *     make text,visible on transition when { condition.value == true }* </pre>
     * This code will change the field <i>text</i> behaviour to <i>visible</i> when fields <i>condition</i> value is equal to <i>true</i>
     * @param field which behaviour will be changed
     * @param behavior one of visible, editable, required, optional, hidden, forbidden
     */
    def make(Field field, Closure behavior) {
        [on: { Transition trans ->
            [when: { Closure condition ->
                if (condition()) {
                    behavior(field, trans)
                    if (!changedFieldsTree.changedFields.containsKey(field.stringId)) {
                        putIntoChangedFields(field, new ChangedField(field.stringId))
                    }
                    changedFieldsTree.addBehavior(field.stringId, useCase.dataSet.get(field.stringId).behavior)
                    addAttributeToChangedField(field, "type", field.type.name)
                }
            }]
        }]
    }

    def saveChangedValue(Field field) {
        useCase.dataSet.get(field.stringId).value = field.value
        if (!changedFieldsTree.changedFields.containsKey(field.stringId)) {
            putIntoChangedFields(field, new ChangedField(field.stringId))
        }
        addAttributeToChangedField(field, "value", field.value)
        addAttributeToChangedField(field, "type", field.type.name)
    }

    def saveChangedChoices(ChoiceField field) {
        useCase.dataSet.get(field.stringId).choices = field.choices
        if (!changedFieldsTree.changedFields.containsKey(field.stringId)) {
            putIntoChangedFields(field, new ChangedField(field.stringId))
        }
        addAttributeToChangedField(field, "choices", field.choices.collect { it.getTranslation(LocaleContextHolder.locale) })
    }

    def saveChangedAllowedNets(CaseField field) {
        useCase.dataSet.get(field.stringId).allowedNets = field.allowedNets
        if (!changedFieldsTree.changedFields.containsKey(field.stringId)) {
            putIntoChangedFields(field, new ChangedField(field.stringId))
        }
        addAttributeToChangedField(field, "allowedNets", field.allowedNets)
    }

    def saveChangedOptions(MapOptionsField field) {
        useCase.dataSet.get(field.stringId).options = field.options
        if (!changedFieldsTree.changedFields.containsKey(field.stringId)) {
            putIntoChangedFields(field, new ChangedField(field.stringId))
        }
        addAttributeToChangedField(field, "options", field.options.collectEntries { key, value -> [key, (value as I18nString).getTranslation(LocaleContextHolder.locale)] })
    }

    def saveChangedValidation(Field field) {
        useCase.dataSet.get(field.stringId).validations = field.validations
        if (!changedFieldsTree.changedFields.containsKey(field.stringId)) {
            putIntoChangedFields(field, new ChangedField(field.stringId))
        }
        List<Validation> compiled = field.validations.collect { it.clone() }
        compiled.findAll { it instanceof DynamicValidation }.collect { (DynamicValidation) it }.each {
            it.compiledRule = dataValidationExpressionEvaluator.compile(useCase, it.expression)
        }
        addAttributeToChangedField(field, "validations", compiled.collect { it.getLocalizedValidation(LocaleContextHolder.locale) })
    }

    void putIntoChangedFields(Field field, ChangedField changedField) {
        putIntoChangedFields(field.stringId, changedField)
    }

    void putIntoChangedFields(String fieldId, ChangedField changedField) {
        changedFieldsTree.put(fieldId, changedField)
    }

    void addAttributeToChangedField(Field field, String attribute, Object value) {
        changedFieldsTree.addAttribute(field.stringId, attribute, value)
    }

    def close = { Transition[] transitions ->
        def service = ApplicationContextProvider.getBean("taskService")
        if (!service) {
            log.error("Could not find task service")
            return
        }

        def transitionIds = transitions.collect { it.stringId } as Set
        service.cancelTasksWithoutReload(transitionIds, useCase.stringId)
    }

    def execute(String taskId) {
        [with : { Map dataSet ->
            executeTasks(dataSet, taskId, { it._id.isNotNull() })
        },
         where: { Closure<Predicate> closure ->
             [with: { Map dataSet ->
                 executeTasks(dataSet, taskId, closure)
             }]
         }]
    }

    void executeTasks(Map dataSet, String taskId, Closure<Predicate> predicateClosure) {
        List<String> caseIds = searchCases(predicateClosure)
        QTask qTask = new QTask("task")
        Page<Task> tasksPage = taskService.searchAll(qTask.transitionId.eq(taskId).and(qTask.caseId.in(caseIds)))
        tasksPage?.content?.each { task ->
            taskService.assignTask(task.stringId)
            dataService.setData(task.stringId, ImportHelper.populateDataset(dataSet as Map<String, Map<String, String>>))
            taskService.finishTask(task.stringId)
        }
    }

    void executeTask(String transitionId, Map dataSet) {
        QTask qTask = new QTask("task")
        Task task = taskService.searchOne(qTask.transitionId.eq(transitionId).and(qTask.caseId.eq(useCase.stringId)))
        taskService.assignTask(task.stringId)
        dataService.setData(task.stringId, ImportHelper.populateDataset(dataSet as Map<String, Map<String, String>>))
        taskService.finishTask(task.stringId)
    }

    List<String> searchCases(Closure<Predicate> predicates) {
        QCase qCase = new QCase("case")
        def expression = predicates(qCase)
        Page<Case> page = workflowService.searchAll(expression)

        return page.content.collect { it.stringId }
    }

    def change(Field field) {
        [about      : { cl -> // TODO: deprecated
            changeFieldValue(field, cl)
        },
         value      : { cl ->
             changeFieldValue(field, cl)
         },
         choices    : { cl ->
             if (!(field instanceof MultichoiceField || field instanceof EnumerationField))
                 return

             def values = cl()
             if (values == null || (values instanceof Closure && values() == UNCHANGED_VALUE))
                 return
             if (!(values instanceof Collection))
                 values = [values]
             field = (ChoiceField) field
             if (values.every { it instanceof I18nString }) {
                 field.setChoices(values as Set<I18nString>)
             } else {
                 field.setChoicesFromStrings(values as Set<String>)
             }
             saveChangedChoices(field)
         },
         allowedNets: { cl ->
             if (!(field instanceof CaseField)) // TODO make this work with FilterField as well
                 return

             def allowedNets = cl()
             if (allowedNets instanceof Closure && allowedNets() == UNCHANGED_VALUE)
                 return

             field = (CaseField) field
             if (allowedNets == null) {
                 field.setAllowedNets(new ArrayList<String>())
             } else if (allowedNets instanceof List) {
                 field.setAllowedNets(allowedNets)
             } else {
                 return
             }
             saveChangedAllowedNets(field)
         },
         options    : { cl ->
             if (!(field instanceof MultichoiceMapField || field instanceof EnumerationMapField))
                 return

             def options = cl()
             if (options == null || (options instanceof Closure && options() == UNCHANGED_VALUE))
                 return
             if (!(options instanceof Map && options.every { it.getKey() instanceof String }))
                 return
             field = (MapOptionsField) field
             if (options.every { it.getValue() instanceof I18nString }) {
                 field.setOptions(options)
             } else {
                 Map<String, I18nString> newOptions = new LinkedHashMap<>();
                 options.each { it -> newOptions.put(it.getKey() as String, new I18nString(it.getValue() as String)) }
                 field.setOptions(newOptions)
             }
             saveChangedOptions(field)
         },
         validations: { cl ->
             changeFieldValidations(field, cl)
         }
        ]
    }

    void changeFieldValue(Field field, def cl) {
        def value = cl()
        if (value instanceof Closure) {
            if (value == initValueOfField) {
                value = initValueOfField(field)

            } else if (value() == UNCHANGED_VALUE) {
                return
            }
        }
        if (value == null && useCase.dataSet.get(field.stringId).value != null) {
            field.clearValue()
            saveChangedValue(field)
        }
        if (value != null) {
            if (field instanceof CaseField) {
                value = ((List) value).stream().map({ entry -> entry instanceof Case ? entry.getStringId() : entry }).collect(Collectors.toList())
                dataService.validateCaseRefValue((List<String>) value, ((CaseField) field).getAllowedNets())
            }
            field.value = value
            saveChangedValue(field)
        }
    }

    void changeFieldValidations(Field field, def cl) {
        def valid = cl()
        if (valid == UNCHANGED_VALUE)
            return
        List<Validation> newValidations = []
        if (valid != null) {
            if (valid instanceof String) {
                newValidations = [new Validation(valid as String)]
            } else if (valid instanceof Validation) {
                newValidations = [valid]
            } else if (valid instanceof Collection) {
                if (valid.every { it instanceof Validation }) {
                    newValidations = valid
                } else {
                    newValidations = valid.collect { new Validation(it as String) }
                }
            }
        }
        field.validations = newValidations
        saveChangedValidation(field)
    }

    def always = { return ALWAYS_GENERATE }
    def once = { return ONCE_GENERATE }

    def generate(String methods, Closure repeated) {
        [into: { Field field ->
            if (field.type == FieldType.FILE)
                File f = new FileGenerateReflection(useCase, field as FileField, repeated() == ALWAYS_GENERATE).callMethod(methods) as File
            else if (field.type == FieldType.TEXT)
                new TextGenerateReflection(useCase, field as TextField, repeated() == ALWAYS_GENERATE).callMethod(methods) as String
            /*if(f != null) {
                useCase.dataSet.get(field.objectId).value = f.name
                field.value = f.name
            }*/
        }]
    }

    def changeCaseProperty(String property) {
        [about: { cl ->
            def value = cl()
            if (value instanceof Closure && value() == UNCHANGED_VALUE) return
            useCase."$property" = value
        }]
    }

    //Cache manipulation
    def cache(String name, Object value) {
        actionsRunner.addToCache("${useCase.stringId}-${name}", value)
    }

    def cache(String name) {
        return actionsRunner.getFromCache("${useCase.stringId}-${name}" as String)
    }

    def cacheFree(String name) {
        actionsRunner.removeFromCache("${useCase.stringId}-${name}")
    }

    //Get PSC - DSL only for Insurance
    def byCode = { String code ->
        return actionsRunner.postalCodeService.findAllByCode(code)
    }

    def byCity = { String city ->
        return actionsRunner.postalCodeService.findAllByCity(city)
    }

    def psc(Closure find, String input) {
        if (find)
            return find(input)
        return null
    }

    def byIco = { String ico ->
        return actionsRunner.orsrService.findByIco(ico)
    }

    def orsr(Closure find, String ico) {
        return find?.call(ico)
    }

    Object get(String key) { map[key] }

    void set(String key, Object value) { map[key] = value }

    List<Case> findCases(Closure<Predicate> predicate) {
        QCase qCase = new QCase("case")
        Page<Case> result = workflowService.searchAll(predicate(qCase))
        return result.content
    }

    List<Case> findCases(Closure<Predicate> predicate, Pageable pageable) {
        QCase qCase = new QCase("case")
        Page<Case> result = workflowService.search(predicate(qCase), pageable)
        return result.content
    }

    Case findCase(Closure<Predicate> predicate) {
        QCase qCase = new QCase("case")
        return workflowService.searchOne(predicate(qCase))
    }

    Case createCase(String identifier, String title = null, String color = "", IUser author = userService.loggedOrSystem, Locale locale = LocaleContextHolder.getLocale()) {
        PetriNet net = petriNetService.getNewestVersionByIdentifier(identifier)
        if (net == null)
            throw new IllegalArgumentException("Petri net with identifier [$identifier] does not exist.")
        return createCase(net, title ?: net.defaultCaseName.getTranslation(locale), color, author)
    }

    Case createCase(PetriNet net, String title = net.defaultCaseName.getTranslation(locale), String color = "", IUser author = userService.loggedOrSystem, Locale locale = LocaleContextHolder.getLocale()) {
        return workflowService.createCase(net.stringId, title, color, author.transformToLoggedUser())
    }

    Task assignTask(String transitionId, Case aCase = useCase, IUser user = userService.loggedOrSystem) {
        String taskId = getTaskId(transitionId, aCase)
        taskService.assignTask(user.transformToLoggedUser(), taskId)
        return taskService.findOne(taskId)
    }

    Task assignTask(Task task, IUser user = userService.loggedOrSystem) {
        taskService.assignTask(task, user)
        return taskService.findOne(task.stringId)
    }

    void assignTasks(List<Task> tasks, IUser assignee = userService.loggedOrSystem) {
        taskService.assignTasks(tasks, assignee)
    }

    void cancelTask(String transitionId, Case aCase = useCase, IUser user = userService.loggedOrSystem) {
        String taskId = getTaskId(transitionId, aCase)
        taskService.cancelTask(user.transformToLoggedUser(), taskId)
    }

    void cancelTask(Task task, IUser user = userService.loggedOrSystem) {
        taskService.cancelTask(task, userService.loggedOrSystem)
    }

    void cancelTasks(List<Task> tasks, IUser user = userService.loggedOrSystem) {
        taskService.cancelTasks(tasks, user)
    }

    void finishTask(String transitionId, Case aCase = useCase, IUser user = userService.loggedOrSystem) {
        String taskId = getTaskId(transitionId, aCase)
        taskService.finishTask(user.transformToLoggedUser(), taskId)
    }

    void finishTask(Task task, IUser user = userService.loggedOrSystem) {
        taskService.finishTask(task, user)
    }

    void finishTasks(List<Task> tasks, IUser finisher = userService.loggedOrSystem) {
        taskService.finishTasks(tasks, finisher)
    }

    List<Task> findTasks(Closure<Predicate> predicate) {
        QTask qTask = new QTask("task")
        Page<Task> result = taskService.searchAll(predicate(qTask))
        return result.content
    }

    List<Task> findTasks(Closure<Predicate> predicate, Pageable pageable) {
        QTask qTask = new QTask("task")
        Page<Task> result = taskService.search(predicate(qTask), pageable)
        return result.content
    }

    Task findTask(Closure<Predicate> predicate) {
        QTask qTask = new QTask("task")
        return taskService.searchOne(predicate(qTask))
    }

    Task findTask(String mongoId) {
        return taskService.searchOne(QTask.task._id.eq(new ObjectId(mongoId)))
    }

    String getTaskId(String transitionId, Case aCase = useCase) {
        List<TaskReference> refs = taskService.findAllByCase(aCase.stringId, null)
        refs.find { it.transitionId == transitionId }.stringId
    }

    IUser assignRole(String roleMongoId, IUser user = userService.loggedUser) {
        // userDetailsService.reloadSecurityContext(userService.getLoggedUser().transformToLoggedUser())
        IUser actualUser = userService.addRole(user, roleMongoId)
        userDetailsService.reloadSecurityContext(actualUser.transformToLoggedUser())
        return actualUser
    }

    IUser assignRole(String roleId, String netId, IUser user = userService.loggedUser) {
        List<PetriNet> nets = petriNetService.getByIdentifier(netId)
        nets.forEach({ net -> user = assignRole(roleId, net, user) })
        return user
    }

    IUser assignRole(String roleId, PetriNet net, IUser user = userService.loggedUser) {
        IUser actualUser = userService.addRole(user, net.roles.values().find { role -> role.importId == roleId }.stringId)
        userDetailsService.reloadSecurityContext(actualUser.transformToLoggedUser())
        return actualUser
    }

    IUser assignRole(String roleId, String netId, Version version, IUser user = userService.loggedUser) {
        PetriNet net = petriNetService.getPetriNet(netId, version)
        return assignRole(roleId, net, user)
    }

    IUser removeRole(String roleMongoId, IUser user = userService.loggedUser) {
        IUser actualUser = userService.removeRole(user, roleMongoId)
        userDetailsService.reloadSecurityContext(actualUser.transformToLoggedUser())
        return actualUser
    }

    IUser removeRole(String roleId, String netId, IUser user = userService.loggedUser) {
        List<PetriNet> nets = petriNetService.getByIdentifier(netId)
        nets.forEach({ net -> user = removeRole(roleId, net, user) })
        return user
    }

    IUser removeRole(String roleId, PetriNet net, IUser user = userService.loggedUser) {
        IUser actualUser = userService.removeRole(user, net.roles.values().find { role -> role.importId == roleId }.stringId)
        userDetailsService.reloadSecurityContext(actualUser.transformToLoggedUser())
        return actualUser
    }

    IUser removeRole(String roleId, String netId, Version version, IUser user = userService.loggedUser) {
        PetriNet net = petriNetService.getPetriNet(netId, version)
        return removeRole(roleId, net, user)
    }

    def setData(Task task, Map dataSet) {
        dataService.setData(task.stringId, ImportHelper.populateDataset(dataSet))
    }

    def setData(Transition transition, Map dataSet) {
        setData(transition.importId, this.useCase, dataSet)
    }

    def setData(String transitionId, Case useCase, Map dataSet) {
        def predicate = QTask.task.caseId.eq(useCase.stringId) & QTask.task.transitionId.eq(transitionId)
        def task = taskService.searchOne(predicate)
        dataService.setData(task.stringId, ImportHelper.populateDataset(dataSet))
    }

    def setDataWithPropagation(String transitionId, Case caze, Map dataSet) {
        Task task = taskService.findOne(caze.tasks.find { it.transition == transitionId }.task)
        return setDataWithPropagation(task, dataSet)
    }

    def setDataWithPropagation(Task task, Map dataSet) {
        return setDataWithPropagation(task.stringId, dataSet)
    }

    def setDataWithPropagation(String taskId, Map dataSet) {
        Task task = taskService.findOne(taskId)
        Case caze = workflowService.findOne(task.caseId)
        ChangedFieldsTree container = setData(task, dataSet)
        caze = workflowService.findOne(caze.stringId)
        this.changedFieldsTree.addPropagated(caze.stringId, makeDataSetIntoChangedFields(dataSet, caze, task))
        this.changedFieldsTree.propagate(container)
        return container
    }

    Map<String, ChangedField> makeDataSetIntoChangedFields(Map<String, Map<String, String>> map, Case caze, Task task) {
        return map.collect { fieldAttributes ->
            ChangedField changedField = new ChangedField(fieldAttributes.key)
            changedField.wasChangedOn(task)
            fieldAttributes.value.each { attribute ->
                changedField.addAttribute(attribute.key, attribute.value)
            }
            return changedField
        }.collectEntries {
            return [(it.id): (it)]
        }
    }

    Map<String, Field> getData(Task task) {
        def useCase = workflowService.findOne(task.caseId);
        return mapData(dataService.getData(task, useCase))
    }

    Map<String, Field> getData(Transition transition) {
        return getData(transition.stringId, this.useCase)
    }

    Map<String, Field> getData(String transitionId, Case useCase) {
        def predicate = QTask.task.caseId.eq(useCase.stringId) & QTask.task.transitionId.eq(transitionId)
        def task = taskService.searchOne(predicate)
        if (!task)
            return new HashMap<String, Field>()
        return mapData(dataService.getData(task, useCase))
    }

    protected Map<String, Field> mapData(List<Field> data) {
        return data.collectEntries {
            [(it.importId): it]
        }
    }
//TODO: Implementacia Group

//    Set<Group> findOrganisation(User user = loggedUser()) {
//        return memberService.findByEmail(user.email)?.groups
//    }
//
//    Group createOrganisation(String name, Group parent = null, Set<User> users = [] as Set) {
//        Group org = new Group(name)
//        if (parent)
//            org.setParentGroup(parent)
//        users.collect { user ->
//            org.addMember(findMember(user))
//        }
//        return groupService.save(org)
//    }
//
//    def deleteOrganisation(Group organisation) {
//        groupService.delete(organisation)
//    }
//
//    Group saveOrganisation(Group organisation) {
//        return groupService.save(organisation)
//    }
//
//    Group removeMember(Group organisation, User user) {
//        organisation.members.removeAll { it.email == user.email }
//        return groupService.save(organisation)
//    }
//
//    Group addMember(Group organisation, User user) {
//        def member = findMember(user)
//        organisation.members.add(member)
//        return groupService.save(organisation)
//    }
//
//    Member findMember(User user) {
//        def member = memberService.findByEmail(user.email)
//        if (member == null)
//            return memberService.save(Member.from(user))
//        return member
//    }

    IUser loggedUser() {
        return userService.loggedUser
    }

    void generatePDF(String transitionId, String fileFieldId) {
        PdfResource pdfResource = ApplicationContextProvider.getBean(PdfResource.class) as PdfResource
        String filename = pdfResource.getOutputDefaultName()
        String storagePath = pdfResource.getOutputFolder() + File.separator + useCase.stringId + "-" + fileFieldId + "-" + pdfResource.getOutputDefaultName()

        pdfResource.setOutputResource(new ClassPathResource(storagePath))
        pdfGenerator.setupPdfGenerator(pdfResource)
        pdfGenerator.generatePdf(useCase, transitionId, pdfResource)
        change useCase.getField(fileFieldId) value { new FileFieldValue(filename, storagePath) }
    }

    void generatePDF(String transitionId, String fileFieldId, List<String> excludedFields) {
        PdfResource pdfResource = ApplicationContextProvider.getBean(PdfResource.class) as PdfResource
        String filename = pdfResource.getOutputDefaultName()
        String storagePath = pdfResource.getOutputFolder() + File.separator + useCase.stringId + "-" + fileFieldId + "-" + pdfResource.getOutputDefaultName()

        pdfResource.setOutputResource(new ClassPathResource(storagePath))
        pdfGenerator.setupPdfGenerator(pdfResource)
        pdfGenerator.generatePdf(useCase, transitionId, pdfResource, excludedFields)
        change useCase.getField(fileFieldId) value { new FileFieldValue(filename, storagePath) }
    }

    void generatePdfWithTemplate(String transitionId, String fileFieldId, String template) {
        PdfResource pdfResource = ApplicationContextProvider.getBean(PdfResource.class) as PdfResource
        String filename = pdfResource.getOutputDefaultName()
        String storagePath = pdfResource.getOutputFolder() + File.separator + useCase.stringId + "-" + fileFieldId + "-" + pdfResource.getOutputDefaultName()

        pdfResource.setOutputResource(new ClassPathResource(storagePath))
        pdfResource.setTemplateResource(new ClassPathResource(template))
        pdfResource.setMarginTitle(100)
        pdfResource.setMarginLeft(75)
        pdfResource.setMarginRight(75)
        pdfResource.updateProperties()
        pdfGenerator.setupPdfGenerator(pdfResource)
        pdfGenerator.generatePdf(useCase, transitionId, pdfResource)
        change useCase.getField(fileFieldId) value { new FileFieldValue(filename, storagePath) }
    }

    void generatePdfWithLocale(String transitionId, String fileFieldId, Locale locale) {
        PdfResource pdfResource = ApplicationContextProvider.getBean(PdfResource.class) as PdfResource
        String filename = pdfResource.getOutputDefaultName()
        String storagePath = pdfResource.getOutputFolder() + File.separator + useCase.stringId + "-" + fileFieldId + "-" + pdfResource.getOutputDefaultName()

        pdfResource.setOutputResource(new ClassPathResource(storagePath))
        pdfResource.setTextLocale(locale)
        pdfGenerator.setupPdfGenerator(pdfResource)
        pdfGenerator.generatePdf(useCase, transitionId, pdfResource)
        change useCase.getField(fileFieldId) value { new FileFieldValue(filename, storagePath) }
    }

    void sendMail(MailDraft mailDraft) {
        mailService.sendMail(mailDraft)
    }

    def changeUserByEmail(String email) {
        [email  : { cl ->
            changeUserByEmail(email, "email", cl)
        },
         name   : { cl ->
             changeUserByEmail(email, "name", cl)
         },
         surname: { cl ->
             changeUserByEmail(email, "surname", cl)
         },
         tel    : { cl ->
             changeUserByEmail(email, "tel", cl)
         },
        ]
    }

    def changeUser(String id) {
        [email  : { cl ->
            changeUser(id, "email", cl)
        },
         name   : { cl ->
             changeUser(id, "name", cl)
         },
         surname: { cl ->
             changeUser(id, "surname", cl)
         },
         tel    : { cl ->
             changeUser(id, "tel", cl)
         },
        ]
    }

    def changeUser(IUser user) {
        [email  : { cl ->
            changeUser(user, "email", cl)
        },
         name   : { cl ->
             changeUser(user, "name", cl)
         },
         surname: { cl ->
             changeUser(user, "surname", cl)
         },
         tel    : { cl ->
             changeUser(user, "tel", cl)
         },
        ]
    }

    def changeUserByEmail(String email, String attribute, def cl) {
        IUser user = userService.findByEmail(email, false)
        changeUser(user, attribute, cl)
    }

    def changeUser(String id, String attribute, def cl) {
        IUser user = userService.findById(id, false)
        changeUser(user, attribute, cl)
    }

    def changeUser(IUser user, String attribute, def cl) {
        if (user == null) {
            log.error("Cannot find user.")
            return
        }

        if (user.hasProperty(attribute) == null) {
            log.error("User object does not have property [" + attribute + "]")
            return
        }

        user[attribute] = cl() as String
        userService.save(user)
    }

    MessageResource inviteUser(String email) {
        NewUserRequest newUserRequest = new NewUserRequest()
        newUserRequest.email = email
        newUserRequest.groups = new HashSet<>()
        newUserRequest.processRoles = new HashSet<>()
        return inviteUser(newUserRequest)
    }

    MessageResource inviteUser(NewUserRequest newUserRequest) {
        IUser user = registrationService.createNewUser(newUserRequest);
        if (user == null)
            return MessageResource.successMessage("Done");
        mailService.sendRegistrationEmail(user);

        mailAttemptService.mailAttempt(newUserRequest.email);
        return MessageResource.successMessage("Done");
    }

    void deleteUser(String email) {
        IUser user = userService.findByEmail(email, false)
        if (user == null)
            log.error("Cannot find user with email [" + email + "]")
        deleteUser(user)
    }

    void deleteUser(IUser user) {
        List<Task> tasks = taskService.findByUser(new FullPageRequest(), user).toList()
        if (tasks != null && tasks.size() > 0)
            taskService.cancelTasks(tasks, user)

        QCase qCase = new QCase("case")
        List<Case> cases = workflowService.searchAll(qCase.author.eq(user.transformToAuthor())).toList()
        if (cases != null)
            cases.forEach({ aCase -> aCase.setAuthor(Author.createAnonymizedAuthor()) })

        userService.deleteUser(user)
    }

    Validation validation(String rule, I18nString message) {
        return new Validation(rule, message)
    }

    DynamicValidation dynamicValidation(String rule, I18nString message) {
        return new DynamicValidation(rule, message)
    }

    List<Case> findFilters(String userInput) {
        return filterSearchService.autocompleteFindFilters(userInput)
    }
}