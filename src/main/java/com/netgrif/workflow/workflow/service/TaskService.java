package com.netgrif.workflow.workflow.service;

import com.netgrif.workflow.auth.domain.IUser;
import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.service.interfaces.IUserService;
import com.netgrif.workflow.elastic.service.interfaces.IElasticTaskMappingService;
import com.netgrif.workflow.elastic.service.interfaces.IElasticTaskService;
import com.netgrif.workflow.event.events.task.*;
import com.netgrif.workflow.petrinet.domain.*;
import com.netgrif.workflow.petrinet.domain.arcs.*;
import com.netgrif.workflow.petrinet.domain.dataset.Field;
import com.netgrif.workflow.petrinet.domain.events.EventPhase;
import com.netgrif.workflow.petrinet.domain.events.EventType;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRole;
import com.netgrif.workflow.petrinet.domain.throwable.TransitionNotExecutableException;
import com.netgrif.workflow.petrinet.service.interfaces.IProcessRoleService;
import com.netgrif.workflow.rules.domain.facts.TransitionEventFact;
import com.netgrif.workflow.rules.service.interfaces.IRuleEngine;
import com.netgrif.workflow.utils.DateUtils;
import com.netgrif.workflow.utils.FullPageRequest;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.EventOutcome;
import com.netgrif.workflow.workflow.domain.Task;
import com.netgrif.workflow.workflow.domain.repositories.TaskRepository;
import com.netgrif.workflow.workflow.domain.triggers.AutoTrigger;
import com.netgrif.workflow.workflow.domain.triggers.TimeTrigger;
import com.netgrif.workflow.workflow.domain.triggers.Trigger;
import com.netgrif.workflow.workflow.service.interfaces.IDataService;
import com.netgrif.workflow.workflow.service.interfaces.ITaskService;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService;
import com.netgrif.workflow.workflow.web.requestbodies.TaskSearchRequest;
import com.netgrif.workflow.workflow.web.responsebodies.TaskReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TaskService implements ITaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    @Autowired
    protected ApplicationEventPublisher publisher;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected IUserService userService;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected TaskSearchService searchService;

    @Autowired
    @Qualifier("taskScheduler")
    protected TaskScheduler scheduler;

    @Autowired
    protected IWorkflowService workflowService;

    @Autowired
    protected IDataService dataService;

    @Autowired
    protected IProcessRoleService processRoleService;

    @Autowired
    protected IElasticTaskMappingService taskMappingService;

    protected IElasticTaskService elasticTaskService;

    @Autowired
    public void setElasticTaskService(IElasticTaskService elasticTaskService) {
        this.elasticTaskService = elasticTaskService;
    }

    @Autowired
    private IRuleEngine ruleEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTasks(List<Task> tasks, IUser user) throws TransitionNotExecutableException {
        for (Task task : tasks) {
            assignTask(task, user);
        }
    }

    @Override
    @Transactional
    public EventOutcome assignTask(LoggedUser loggedUser, String taskId) throws TransitionNotExecutableException {
        Optional<Task> taskOptional = taskRepository.findById(taskId);
        if (!taskOptional.isPresent())
            throw new IllegalArgumentException("Could not find task with id [" + taskId + "]");

        IUser user = userService.resolveById(loggedUser.getId(), true);
        return assignTask(taskOptional.get(), user);
    }

    @Override
    public EventOutcome assignTask(String taskId) throws TransitionNotExecutableException {
        LoggedUser user = userService.getLoggedOrSystem().transformToLoggedUser();
        return assignTask(user, taskId);
    }

    @Override
    @Transactional
    public EventOutcome assignTask(Task task, IUser user) throws TransitionNotExecutableException {
        Case useCase = workflowService.findOne(task.getCaseId());
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());
        EventOutcome outcome = new EventOutcome(task.getStringId(), task.getTransitionId(), useCase.getStringId(), transition.getAssignMessage());

        outcome.add(dataService.runActions(transition.getPreAssignActions(), useCase.getStringId(), task, transition));
        useCase = evaluateRules(useCase.getStringId(), task, EventType.ASSIGN, EventPhase.PRE);
        assignTaskToUser(user, task, useCase.getStringId());
        outcome.add(dataService.runActions(transition.getPostAssignActions(), useCase.getStringId(), task, transition));
        useCase = evaluateRules(useCase.getStringId(), task, EventType.ASSIGN, EventPhase.POST);

        addTaskStateInformationToEventOutcome(outcome, task);

        publisher.publishEvent(new UserAssignTaskEvent(user, task, useCase));
        log.info("[" + useCase.getStringId() + "]: Task [" + task.getTitle() + "] in case [" + useCase.getTitle() + "] assigned to [" + user.getEmail() + "]");
        return outcome;
    }

    @Transactional
    protected void assignTaskToUser(IUser user, Task task, String useCaseId) throws TransitionNotExecutableException {
        Case useCase = workflowService.findOne(useCaseId);
        useCase.getPetriNet().initializeArcs();
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());

        log.info("[" + useCaseId + "]: Assigning task [" + task.getTitle() + "] to user [" + user.getEmail() + "]");

        startExecution(transition, useCase);
        task.setUserId(user.getStringId());
        task.setStartDate(LocalDateTime.now());

        useCase = workflowService.save(useCase);
        save(task);
        reloadTasks(useCase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishTasks(List<Task> tasks, IUser user) throws TransitionNotExecutableException {
        for (Task task : tasks) {
            finishTask(task, user);
        }
    }

    @Override
    public EventOutcome finishTask(String taskId) throws IllegalArgumentException, TransitionNotExecutableException {
        LoggedUser user = userService.getLoggedOrSystem().transformToLoggedUser();
        return finishTask(user, taskId);
    }

    @Override
    @Transactional
    public EventOutcome finishTask(LoggedUser loggedUser, String taskId) throws IllegalArgumentException, TransitionNotExecutableException {
        Optional<Task> taskOptional = taskRepository.findById(taskId);
        if (!taskOptional.isPresent())
            throw new IllegalArgumentException("Could not find task with id [" + taskId + "]");
        Task task = taskOptional.get();
        IUser user = userService.resolveById(loggedUser.getId(), true);

        if (task.getUserId() == null) {
            throw new IllegalArgumentException("Task with id=" + taskId + " is not assigned to any user.");
        }
        // TODO: 14. 4. 2017 replace with @PreAuthorize
        if (!task.getUserId().equals(loggedUser.getId()) && !loggedUser.isAnonymous()) {
            throw new IllegalArgumentException("User that is not assigned tried to finish task");
        }

        return finishTask(task, user);
    }

    @Override
    @Transactional
    public EventOutcome finishTask(Task task, IUser user) throws TransitionNotExecutableException {
        Case useCase = workflowService.findOne(task.getCaseId());
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());
        EventOutcome outcome = new EventOutcome(task.getStringId(), task.getTransitionId(), useCase.getStringId(), transition.getFinishMessage());

        log.info("[" + useCase.getStringId() + "]: Finishing task [" + task.getTitle() + "] to user [" + user.getEmail() + "]");

        validateData(transition, useCase);
        outcome.add(dataService.runActions(transition.getPreFinishActions(), useCase.getStringId(), task, transition));
        useCase = evaluateRules(useCase.getStringId(), task, EventType.FINISH, EventPhase.PRE);

        finishExecution(transition, useCase.getStringId());
        task.setFinishDate(LocalDateTime.now());
        task.setFinishedBy(task.getUserId());
        task.setUserId(null);

        useCase = workflowService.findOne(useCase.getStringId());
        save(task);
        reloadTasks(useCase);
        outcome.add(dataService.runActions(transition.getPostFinishActions(), useCase.getStringId(), task, transition));
        useCase = evaluateRules(useCase.getStringId(), task, EventType.FINISH, EventPhase.POST);

        addTaskStateInformationToEventOutcome(outcome, task);

        publisher.publishEvent(new UserFinishTaskEvent(user, task, useCase));
        log.info("[" + useCase.getStringId() + "]: Task [" + task.getTitle() + "] in case [" + useCase.getTitle() + "] assigned to [" + user.getEmail() + "] was finished");

        return outcome;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTasks(List<Task> tasks, IUser user) {
        for (Task task : tasks) {
            cancelTask(task, user);
        }
    }

    @Override
    @Transactional
    public EventOutcome cancelTask(LoggedUser loggedUser, String taskId) {
        Optional<Task> taskOptional = taskRepository.findById(taskId);
        if (!taskOptional.isPresent())
            throw new IllegalArgumentException("Could not find task with id [" + taskId + "]");

        IUser user = userService.resolveById(loggedUser.getId(), true);
        return cancelTask(taskOptional.get(), user);
    }

    @Override
    @Transactional
    public EventOutcome cancelTask(Task task, IUser user) {
        Case useCase = workflowService.findOne(task.getCaseId());
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());
        EventOutcome outcome = new EventOutcome(task.getStringId(), task.getTransitionId(), useCase.getStringId(), transition.getCancelMessage());

        log.info("[" + useCase.getStringId() + "]: Canceling task [" + task.getTitle() + "] to user [" + user.getEmail() + "]");

        outcome.add(dataService.runActions(transition.getPreCancelActions(), useCase.getStringId(), task, transition));
        useCase = evaluateRules(useCase.getStringId(), task, EventType.CANCEL, EventPhase.PRE);
        task = returnTokens(task, useCase.getStringId());
        useCase = workflowService.findOne(useCase.getStringId());
        reloadTasks(useCase);
        outcome.add(dataService.runActions(transition.getPostCancelActions(), useCase.getStringId(), task, transition));
        useCase = evaluateRules(useCase.getStringId(), task, EventType.CANCEL, EventPhase.POST);

        addTaskStateInformationToEventOutcome(outcome, task);

        publisher.publishEvent(new UserCancelTaskEvent(user, task, useCase));
        log.info("[" + useCase.getStringId() + "]: Task [" + task.getTitle() + "] in case [" + useCase.getTitle() + "] assigned to [" + user.getEmail() + "] was cancelled");
        return outcome;
    }

    /**
     * Used in cancel task action
     */
    @Override
    public void cancelTasksWithoutReload(Set<String> transitions, String caseId) {
        List<Task> tasks = taskRepository.findAllByTransitionIdInAndCaseId(transitions, caseId);
        Case useCase = null;
        for (Task task : tasks) {
            if (task.getUserId() != null) {
                if (useCase == null)
                    useCase = workflowService.findOne(task.getCaseId());
                Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());
                dataService.runActions(transition.getPreCancelActions(), useCase.getStringId(), task, transition);
                returnTokens(task, useCase.getStringId());
                dataService.runActions(transition.getPostCancelActions(), useCase.getStringId(), task, transition);
            }
        }
    }

    private Task returnTokens(Task task, String useCaseId) {
        Case useCase = workflowService.findOne(useCaseId);
        PetriNet net = useCase.getPetriNet();
        net.getArcsOfTransition(task.getTransitionId()).stream()
                .filter(arc -> arc.getSource() instanceof Place)
                .forEach(arc -> {
                    arc.rollbackExecution(useCase.getConsumedTokens().get(arc.getStringId()));
                    useCase.getConsumedTokens().remove(arc.getStringId());
                });
        workflowService.updateMarking(useCase);

        task.setUserId(null);
        task.setStartDate(null);
        task = save(task);
        workflowService.save(useCase);

        return task;
    }

    @Override
    @Transactional
    public EventOutcome delegateTask(LoggedUser loggedUser, String delegatedId, String taskId) throws TransitionNotExecutableException {
        IUser delegatedUser = userService.resolveById(delegatedId, true);
        IUser delegateUser = userService.resolveById(loggedUser.getId(), true);
        Optional<Task> taskOptional = taskRepository.findById(taskId);
        if (!taskOptional.isPresent())
            throw new IllegalArgumentException("Could not find task with id [" + taskId + "]");
        Task task = taskOptional.get();

        Case useCase = workflowService.findOne(task.getCaseId());
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());
        EventOutcome outcome = new EventOutcome(task.getStringId(), task.getTransitionId(), useCase.getStringId(), transition.getDelegateMessage());

        log.info("[" + useCase.getStringId() + "]: Delegating task [" + task.getTitle() + "] to user [" + delegatedUser.getEmail() + "]");

        outcome.add(dataService.runActions(transition.getPreDelegateActions(), useCase.getStringId(), task, transition));
        useCase = evaluateRules(useCase.getStringId(), task, EventType.DELEGATE, EventPhase.PRE);
        delegate(delegatedUser, task, useCase);
        outcome.add(dataService.runActions(transition.getPostDelegateActions(), useCase.getStringId(), task, transition));
        useCase = evaluateRules(useCase.getStringId(), task, EventType.DELEGATE, EventPhase.POST);

        useCase = workflowService.findOne(useCase.getStringId());
        reloadTasks(useCase);

        addTaskStateInformationToEventOutcome(outcome, task);

        publisher.publishEvent(new UserDelegateTaskEvent(delegateUser, task, useCase, delegatedUser));
        log.info("Task [" + task.getTitle() + "] in case [" + useCase.getTitle() + "] assigned to [" + delegateUser.getEmail() + "] was delegated to [" + delegatedUser.getEmail() + "]");

        return outcome;
    }

    protected void delegate(IUser delegated, Task task, Case useCase) throws TransitionNotExecutableException {
        if (task.getUserId() != null) {
            task.setUserId(delegated.getStringId());
            save(task);
        } else {
            assignTaskToUser(delegated, task, useCase.getStringId());
        }
    }

    protected Case evaluateRules(String caseId, Task task, EventType eventType, EventPhase eventPhase) {
        Case useCase = workflowService.findOne(caseId);
        log.info("[" + useCase.getStringId() + "]: Task [" + task.getTitle() + "] in case [" + useCase.getTitle() + "] evaluating rules of event " + eventType.name() + " of phase " + eventPhase.name());
        ruleEngine.evaluateRules(useCase, task, TransitionEventFact.of(task, eventType, eventPhase));
        return workflowService.save(useCase);
    }

    protected void addTaskStateInformationToEventOutcome(EventOutcome outcome, Task task) {
        Optional<Task> taskOptional = taskRepository.findById(task.getStringId());
        if (!taskOptional.isPresent())
            return;
        String assigneeId = taskOptional.get().getUserId();
        if (assigneeId != null)
            outcome.setAssignee(userService.resolveById(assigneeId, true));
        outcome.setStartDate(task.getStartDate());
        outcome.setFinishDate(task.getFinishDate());
        outcome.setTaskId(task.getStringId());
    }

    /**
     * Reloads all unassigned tasks of given case:
     * <table border="1">
     * <tr>
     * <td></td><td>LocalisedTask is present</td><td>LocalisedTask is not present</td>
     * </tr>
     * <tr>
     * <td>Transition executable</td><td>no action</td><td>create task</td>
     * </tr>
     * <tr>
     * <td>Transition not executable</td><td>destroy task</td><td>no action</td>
     * </tr>
     * </table>
     */
    @Override
    @Transactional
    public void reloadTasks(Case useCase) {
        log.info("[" + useCase.getStringId() + "]: Reloading tasks in [" + useCase.getTitle() + "]");
        PetriNet net = useCase.getPetriNet();

        net.getTransitions().values().forEach(transition -> {
            List<Task> tasks = taskRepository.findAllByCaseId(useCase.getStringId());
            if (isExecutable(transition, net)) {
                if (taskIsNotPresent(tasks, transition)) {
                    createFromTransition(transition, useCase);
                }
            } else {
                deleteUnassignedNotExecutableTasks(tasks, transition, useCase);
            }
        });
        String sysemId = userService.getSystem().getStringId();
        List<Task> tasks = taskRepository.findAllByCaseId(useCase.getStringId());
        if (tasks.stream().anyMatch(task -> Objects.equals(task.getUserId(), sysemId) && task.getStartDate() != null)) {
            return;
        }
        for (Task task : tasks) {
            if (Objects.equals(task.getUserId(), sysemId) && task.getStartDate() == null) {
                executeTransition(task, workflowService.findOne(useCase.getStringId()));
                return;
            }
            resolveUserRef(task, useCase);
        }
    }

    @Transactional
    void deleteUnassignedNotExecutableTasks(List<Task> tasks, Transition transition, Case useCase) {
        delete(tasks.stream()
                .filter(task -> task.getTransitionId().equals(transition.getStringId()) && task.getUserId() == null)
                .collect(Collectors.toList()), useCase);
    }

    @Transactional
    boolean taskIsNotPresent(List<Task> tasks, Transition transition) {
        return tasks.stream().noneMatch(task -> task.getTransitionId().equals(transition.getStringId()));
    }

    @Transactional
    boolean isExecutable(Transition transition, PetriNet net) {
        Collection<Arc> arcsOfTransition = net.getArcsOfTransition(transition);

        if (arcsOfTransition == null)
            return true;

        return arcsOfTransition.stream()
                .filter(arc -> arc.getDestination().equals(transition)) // todo: from same source error
                .allMatch(Arc::isExecutable);
    }

    @Transactional
    void finishExecution(Transition transition, String useCaseId) throws TransitionNotExecutableException {
        Case useCase = workflowService.findOne(useCaseId);
        log.info("[" + useCaseId + "]: Finish execution of task [" + transition.getTitle() + "] in case [" + useCase.getTitle() + "]");
        execute(transition, useCase, arc -> arc.getSource().equals(transition));
        Supplier<Stream<Arc>> arcStreamSupplier = () -> useCase.getPetriNet().getArcsOfTransition(transition.getStringId()).stream();
        arcStreamSupplier.get().filter(arc -> useCase.getConsumedTokens().containsKey(arc.getStringId())).forEach(arc -> useCase.getConsumedTokens().remove(arc.getStringId()));
        workflowService.save(useCase);
    }

    @Transactional
    public void startExecution(Transition transition, Case useCase) throws TransitionNotExecutableException {
        log.info("[" + useCase.getStringId() + "]: Start execution of " + transition.getTitle() + " in case " + useCase.getTitle());
        execute(transition, useCase, arc -> arc.getDestination().equals(transition));
    }

    @Transactional
    protected void execute(Transition transition, Case useCase, Predicate<Arc> predicate) throws TransitionNotExecutableException {
        Supplier<Stream<Arc>> filteredSupplier = () -> useCase.getPetriNet().getArcsOfTransition(transition.getStringId()).stream().filter(predicate);

        if (!filteredSupplier.get().allMatch(Arc::isExecutable))
            throw new TransitionNotExecutableException("Not all arcs can be executed task [" + transition.getStringId() + "] in case [" + useCase.getTitle() + "]");

        filteredSupplier.get().sorted((o1, o2) -> ArcOrderComparator.getInstance().compare(o1, o2)).forEach(arc -> {
            if (arc instanceof ResetArc) {
                useCase.getConsumedTokens().put(arc.getStringId(), ((Place) arc.getSource()).getTokens());
            }
            if(arc.getReference() != null && arc.getSource() instanceof Place){
                useCase.getConsumedTokens().put(arc.getStringId(), arc.getReference().getMultiplicity());
            }
            arc.execute();
        });

        workflowService.updateMarking(useCase);
    }

    @Transactional
    protected EventOutcome executeTransition(Task task, Case useCase) {
        log.info("[" + useCase.getStringId() + "]: executeTransition [" + task.getTransitionId() + "] in case [" + useCase.getTitle() + "]");
        useCase = workflowService.decrypt(useCase);
        EventOutcome outcome = new EventOutcome(task.getStringId(), task.getTransitionId(), useCase.getStringId());
        try {
            log.info("assignTask [" + task.getTitle() + "] in case [" + useCase.getTitle() + "]");
            assignTask(task.getStringId());
            log.info("getData [" + task.getTitle() + "] in case [" + useCase.getTitle() + "]");
            dataService.getData(task.getStringId());
            log.info("finishTask [" + task.getTitle() + "] in case [" + useCase.getTitle() + "]");
            finishTask(task.getStringId());

            outcome.setMessage(new I18nString("Task " + task.getTitle() + " executed"));
        } catch (TransitionNotExecutableException e) {
            log.error("execution of task [" + task.getTitle() + "] in case [" + useCase.getTitle() + "] failed: ", e);
        }
        return outcome;
    }

    @Transactional
    void validateData(Transition transition, Case useCase) {
        for (Map.Entry<String, DataFieldLogic> entry : transition.getDataSet().entrySet()) {
            if (!useCase.getDataField(entry.getKey()).isRequired(transition.getImportId()))
                continue;
            if (useCase.getDataField(entry.getKey()).isUndefined(transition.getImportId()) && !entry.getValue().isRequired())
                continue;

            Object value = useCase.getDataSet().get(entry.getKey()).getValue();
            if (value == null) {
                Field field = useCase.getField(entry.getKey());
                throw new IllegalArgumentException("Field \"" + field.getName() + "\" has null value");
            }
            if (value instanceof String && ((String) value).isEmpty()) {
                Field field = useCase.getField(entry.getKey());
                throw new IllegalArgumentException("Field \"" + field.getName() + "\" has empty value");
            }
        }
    }

    @Transactional
    protected void scheduleTaskExecution(Task task, LocalDateTime time, Case useCase) {
        log.info("[" + useCase.getStringId() + "]: Task " + task.getTitle() + " scheduled to run at " + time.toString());
        scheduler.schedule(() -> {
            try {
                executeTransition(task, useCase);
            } catch (Exception e) {
                log.info("[" + useCase.getStringId() + "]: Scheduled task [" + task.getTitle() + "] of case [" + useCase.getTitle() + "] could not be executed: " + e);
            }
        }, DateUtils.localDateTimeToDate(time));
        publisher.publishEvent(new TimeFinishTaskEvent(time, task, useCase));
    }

    @Override
    public Task findOne(String taskId) {
        Optional<Task> optionalTask = taskRepository.findById(taskId);
        if (!optionalTask.isPresent())
            throw new IllegalArgumentException("Could not find task with id [" + taskId + "]");
        return optionalTask.get();
    }

    @Override
    public Page<Task> getAll(LoggedUser loggedUser, Pageable pageable, Locale locale) {
        List<Task> tasks;
        if (loggedUser.getProcessRoles().isEmpty()) {
            tasks = new ArrayList<>();
            return new PageImpl<>(tasks, pageable, 0L);
        } else {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("{$or:[");
            loggedUser.getProcessRoles().forEach(role -> {
                queryBuilder.append("{\"roles.");
                queryBuilder.append(role);
                queryBuilder.append("\":{$exists:true}},");
            });
            if (!loggedUser.getProcessRoles().isEmpty())
                queryBuilder.deleteCharAt(queryBuilder.length() - 1);
            else
                queryBuilder.append("{}");
            queryBuilder.append("]}");
            BasicQuery query = new BasicQuery(queryBuilder.toString());
            query = (BasicQuery) query.with(pageable);
            tasks = mongoTemplate.find(query, Task.class);
            return loadUsers(new PageImpl<>(tasks, pageable,
                    mongoTemplate.count(new BasicQuery(queryBuilder.toString(), "{_id:1}"), Task.class)));
        }
    }

    @Override
    public Page<Task> search(List<TaskSearchRequest> requests, Pageable pageable, LoggedUser user, Locale locale, Boolean isIntersection) {
        com.querydsl.core.types.Predicate searchPredicate = searchService.buildQuery(requests, user, locale, isIntersection);
        if (searchPredicate != null) {
            Page<Task> page = taskRepository.findAll(searchPredicate, pageable);
            page = loadUsers(page);
            page = dataService.setImmediateFields(page);
            return page;
        } else {
            return Page.empty();
        }
    }

    @Override
    public long count(List<TaskSearchRequest> requests, LoggedUser user, Locale locale, Boolean isIntersection) {
        com.querydsl.core.types.Predicate searchPredicate = searchService.buildQuery(requests, user, locale, isIntersection);
        if (searchPredicate != null)
            return taskRepository.count(searchPredicate);
        else
            return 0;
    }

    @Override
    public Page<Task> findByCases(Pageable pageable, List<String> cases) {
        return loadUsers(taskRepository.findByCaseIdIn(pageable, cases));
    }

    @Override
    public Task findById(String id) {
        Optional<Task> taskOptional = taskRepository.findById(id);
        if (!taskOptional.isPresent())
            throw new IllegalArgumentException("Could not find task with id [" + id + "]");
        Task task = taskOptional.get();
        this.setUser(task);
        return task;
    }

    @Override
    public List<Task> findAllById(List<String> ids) {
        List<Task> page = new LinkedList<>();
        ids.forEach(id -> {
            Optional<Task> task = taskRepository.findById(id);
            task.ifPresent(page::add);
        });
        if (page.size() > 0) {
            page.forEach(this::setUser);
        }
        return page;
    }

    @Override
    @Transactional
    public void createTasks(Case useCase) {
        PetriNet net = useCase.getPetriNet();
        Collection<Transition> transitions = net.getTransitions().values();

        for (Transition transition : transitions) {
            if (isExecutable(transition, net)) {
                Task task = createFromTransition(transition, useCase);
                // TODO: 16. 3. 2017 there should be some fancy logic
//                task.setAssignRole(net.getRoles().get(transition.getRoles().keySet().stream().findFirst().orElseGet(null)).getStringId());
                //figureOutProcessRoles(task, transition);
                if (task == null)
                    break;
                save(task);
            }
        }
    }

    @Override
    public Page<Task> findByUser(Pageable pageable, IUser user) {
        return loadUsers(taskRepository.findByUserId(pageable, user.getStringId()));
    }

    @Override
    public Page<Task> findByTransitions(Pageable pageable, List<String> transitions) {
        return loadUsers(taskRepository.findByTransitionIdIn(pageable, transitions));
    }

    @Override
    public Page<Task> searchAll(com.querydsl.core.types.Predicate predicate) {
        Page<Task> tasks = taskRepository.findAll(predicate, new FullPageRequest());
        return loadUsers(tasks);
    }

    @Override
    public Page<Task> search(com.querydsl.core.types.Predicate predicate, Pageable pageable) {
        Page<Task> tasks = taskRepository.findAll(predicate, pageable);
        return loadUsers(tasks);
    }

    @Override
    public Task searchOne(com.querydsl.core.types.Predicate predicate) {
        Page<Task> tasks = taskRepository.findAll(predicate, PageRequest.of(0, 1));
        if (tasks.getTotalElements() > 0)
            return tasks.getContent().get(0);
        return null;
    }

    @Override
    public List<TaskReference> findAllByCase(String caseId, Locale locale) {
        return taskRepository.findAllByCaseId(caseId).stream()
                .map(task -> new TaskReference(task.getStringId(), task.getTitle().getTranslation(locale), task.getTransitionId()))
                .collect(Collectors.toList());
    }

    @Override
    public Task save(Task task) {
        task = taskRepository.save(task);
        elasticTaskService.index(this.taskMappingService.transform(task));
        return task;
    }

    @Override
    public void resolveUserRef(Case useCase) {
        useCase.getTasks().forEach(taskPair -> {
            Optional<Task> taskOptional = taskRepository.findById(taskPair.getTask());
            taskOptional.ifPresent(task -> resolveUserRef(task, useCase));
        });

    }

    @Override
    public Task resolveUserRef(Task task, Case useCase) {
        task.getUsers().clear();
        task.getNegativeViewUsers().clear();
        task.getUserRefs().forEach((id, permission) -> {
            List<String> userIds = getExistingUsers((List<String>) useCase.getDataSet().get(id).getValue());
            if (userIds != null && userIds.size() != 0 && permission.containsKey("view") && !permission.get("view")) {
                task.getNegativeViewUsers().addAll(userIds);
            } else if (userIds != null && userIds.size() != 0) {
                task.addUsers(new HashSet<>(userIds), permission);
            }
        });
        return taskRepository.save(task);
    }

    private List<String> getExistingUsers(List<String> userIds) {
        if (userIds == null)
            return null;
        return userIds.stream().filter(userId -> userService.resolveById(userId, false) != null).collect(Collectors.toList());
    }

    private Task createFromTransition(Transition transition, Case useCase) {
        final Task task = Task.with()
                .title(transition.getTitle())
                .processId(useCase.getPetriNetId())
                .caseId(useCase.get_id().toString())
                .transitionId(transition.getImportId())
                .layout(transition.getLayout())
                .caseColor(useCase.getColor())
                .caseTitle(useCase.getTitle())
                .priority(transition.getPriority())
                .icon(transition.getIcon() == null ? useCase.getIcon() : transition.getIcon())
                .immediateDataFields(new LinkedHashSet<>(transition.getImmediateData()))
                .assignPolicy(transition.getAssignPolicy())
                .dataFocusPolicy(transition.getDataFocusPolicy())
                .finishPolicy(transition.getFinishPolicy())
                .build();
        transition.getEvents().forEach((type, event) -> task.addEventTitle(type, event.getTitle()));
        task.addAssignedUserPolicy(transition.getAssignedUserPolicy());
        for (Trigger trigger : transition.getTriggers()) {
            Trigger taskTrigger = trigger.clone();
            task.addTrigger(taskTrigger);

            if (taskTrigger instanceof TimeTrigger) {
                TimeTrigger timeTrigger = (TimeTrigger) taskTrigger;
                scheduleTaskExecution(task, timeTrigger.getStartDate(), useCase);
            } else if (taskTrigger instanceof AutoTrigger) {
                task.setUserId(userService.getSystem().getStringId());
            }
        }
        ProcessRole defaultRole = processRoleService.defaultRole();
        for (Map.Entry<String, Map<String, Boolean>> entry : transition.getRoles().entrySet()) {
            if (useCase.getEnabledRoles().contains(entry.getKey()) || defaultRole.getStringId().equals(entry.getKey())) {
                task.addRole(entry.getKey(), entry.getValue());
            }
        }
        transition.getNegativeViewRoles().forEach(task::addNegativeViewRole);

        for (Map.Entry<String, Map<String, Boolean>> entry : transition.getUserRefs().entrySet()) {
            task.addUserRef(entry.getKey(), entry.getValue());
        }

        Transaction transaction = useCase.getPetriNet().getTransactionByTransition(transition);
        if (transaction != null) {
            task.setTransactionId(transaction.getStringId());
        }

        resolveUserRef(task, useCase);
        Task savedTask = save(task);

        useCase.addTask(savedTask);
        useCase = workflowService.save(useCase);

        publisher.publishEvent(new CreateTaskEvent(savedTask, useCase));

        return savedTask;
    }

    private Page<Task> loadUsers(Page<Task> tasks) {
        Map<String, IUser> users = new HashMap<>();
        tasks.forEach(task -> {
            if (task.getUserId() != null) {
                if (users.containsKey(task.getUserId()))
                    task.setUser(users.get(task.getUserId()));
                else {
                    task.setUser(userService.resolveById(task.getUserId(), true));
                    users.put(task.getUserId(), task.getUser());
                }
            }
        });

        return tasks;
    }

    @Override
    public void delete(Iterable<? extends Task> tasks, Case useCase) {
        workflowService.removeTasksFromCase(tasks, useCase);
        taskRepository.deleteAll(tasks);
        tasks.forEach(t -> elasticTaskService.remove(t.getStringId()));
    }

    @Override
    public void delete(Iterable<? extends Task> tasks, String caseId) {
        workflowService.removeTasksFromCase(tasks, caseId);
        taskRepository.deleteAll(tasks);
        tasks.forEach(t -> elasticTaskService.remove(t.getStringId()));
    }

    @Override
    public void deleteTasksByCase(String caseId) {
        delete(taskRepository.findAllByCaseId(caseId), caseId);
    }

    @Override
    public void deleteTasksByPetriNetId(String petriNetId) {
        taskRepository.deleteAllByProcessId(petriNetId);
    }

    private void setUser(Task task) {
        if (task.getUserId() != null)
            task.setUser(userService.resolveById(task.getUserId(), true));
    }
}