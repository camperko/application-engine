package com.netgrif.workflow.workflow.service;

import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.auth.service.interfaces.IUserService;
import com.netgrif.workflow.event.events.task.*;
import com.netgrif.workflow.petrinet.domain.*;
import com.netgrif.workflow.petrinet.domain.arcs.Arc;
import com.netgrif.workflow.petrinet.domain.arcs.ResetArc;
import com.netgrif.workflow.petrinet.domain.dataset.Field;
import com.netgrif.workflow.petrinet.domain.roles.RolePermission;
import com.netgrif.workflow.petrinet.domain.throwable.TransitionNotExecutableException;
import com.netgrif.workflow.utils.DateUtils;
import com.netgrif.workflow.utils.FullPageRequest;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.Task;
import com.netgrif.workflow.workflow.domain.repositories.TaskRepository;
import com.netgrif.workflow.workflow.domain.triggers.AutoTrigger;
import com.netgrif.workflow.workflow.domain.triggers.TimeTrigger;
import com.netgrif.workflow.workflow.domain.triggers.Trigger;
import com.netgrif.workflow.workflow.service.interfaces.IDataService;
import com.netgrif.workflow.workflow.service.interfaces.ITaskService;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService;
import com.netgrif.workflow.workflow.web.responsebodies.TaskReference;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final Logger log = Logger.getLogger(TaskService.class);

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private IUserService userService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TaskSearchService searchService;

    @Autowired
    private TaskScheduler scheduler;

    @Autowired
    private IWorkflowService workflowService;

    @Autowired
    private IDataService dataService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTasks(List<Task> tasks, User user) throws TransitionNotExecutableException {
        for (Task task : tasks) {
            assignTask(task, user);
        }
    }

    @Override
    @Transactional
    public EventOutcome assignTask(LoggedUser loggedUser, String taskId) throws TransitionNotExecutableException {
        Task task = taskRepository.findOne(taskId);
        User user = userService.findById(loggedUser.getId(), true);
        return assignTask(task, user);
    }

    @Override
    public EventOutcome assignTask(String taskId) throws TransitionNotExecutableException {
        LoggedUser user = userService.getLoggedOrSystem().transformToLoggedUser();
        return assignTask(user, taskId);
    }

    @Override
    @Transactional
    public EventOutcome assignTask(Task task, User user) throws TransitionNotExecutableException {
        Case useCase = workflowService.findOne(task.getCaseId());
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());
        EventOutcome outcome = new EventOutcome(transition.getAssignMessage());

        outcome.add(dataService.runActions(transition.getPreAssignActions(), useCase.getStringId(), transition));
        assignTaskToUser(user, task, useCase.getStringId());
        outcome.add(dataService.runActions(transition.getPostAssignActions(), useCase.getStringId(), transition));

        publisher.publishEvent(new UserAssignTaskEvent(user, task, useCase));
        log.info("Task [" + task.getTitle() + "] in case [" + useCase.getTitle() + "] assigned to [" + user.getEmail() + "]");
        return outcome;
    }

    @Transactional
    protected void assignTaskToUser(User user, Task task, String useCaseId) throws TransitionNotExecutableException {
        Case useCase = workflowService.findOne(useCaseId);
        useCase.getPetriNet().initializeArcs();// TODO: 19/06/2017 remove?
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());

        startExecution(transition, useCase);
        task.setUserId(user.getId());
        task.setStartDate(LocalDateTime.now());

        useCase = workflowService.save(useCase);
        taskRepository.save(task);
        reloadTasks(useCase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishTasks(List<Task> tasks, User user) throws TransitionNotExecutableException {
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
        Task task = taskRepository.findOne(taskId);
        User user = userService.findById(loggedUser.getId(), true);
        if (task == null) {
            throw new IllegalArgumentException("Could not find task with id=" + taskId);
        } else if (task.getUserId() == null) {
            throw new IllegalArgumentException("Task with id=" + taskId + " is not assigned to any user.");
        }
        // TODO: 14. 4. 2017 replace with @PreAuthorize
        if (!task.getUserId().equals(loggedUser.getId())) {
            throw new IllegalArgumentException("User that is not assigned tried to finish task");
        }

        return finishTask(task, user);
    }

    @Override
    @Transactional
    public EventOutcome finishTask(Task task, User user) throws TransitionNotExecutableException {
        Case useCase = workflowService.findOne(task.getCaseId());
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());
        EventOutcome outcome = new EventOutcome(transition.getFinishMessage());

        validateData(transition, useCase);
        outcome.add(dataService.runActions(transition.getPreFinishActions(), useCase.getStringId(), transition));
        finishExecution(transition, useCase.getStringId());
        task.setFinishDate(LocalDateTime.now());
        task.setFinishedBy(task.getUserId());
        task.setUserId(null);

        useCase = workflowService.findOne(useCase.getStringId());
        taskRepository.save(task);
        reloadTasks(useCase);
        outcome.add(dataService.runActions(transition.getPostFinishActions(), useCase.getStringId(), transition));

        publisher.publishEvent(new UserFinishTaskEvent(user, task, useCase));
        log.info("Task [" + task.getTitle() + "] in case [" + useCase.getTitle() + "] assigned to [" + user.getEmail() + "] was finished");

        return outcome;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTasks(List<Task> tasks, User user) {
        for (Task task : tasks) {
            cancelTask(task, user);
        }
    }

    @Override
    @Transactional
    public EventOutcome cancelTask(LoggedUser loggedUser, String taskId) {
        Task task = taskRepository.findOne(taskId);
        User user = userService.findById(loggedUser.getId(), true);
        return cancelTask(task, user);
    }

    @Override
    @Transactional
    public EventOutcome cancelTask(Task task, User user) {
        Case useCase = workflowService.findOne(task.getCaseId());
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());
        EventOutcome outcome = new EventOutcome(transition.getCancelMessage());

        outcome.add(dataService.runActions(transition.getPreCancelActions(), useCase.getStringId(), transition));
        task = returnTokens(task, useCase.getStringId());
        outcome.add(dataService.runActions(transition.getPostCancelActions(), useCase.getStringId(), transition));
        useCase = workflowService.findOne(useCase.getStringId());
        reloadTasks(useCase);

        publisher.publishEvent(new UserCancelTaskEvent(user, task, useCase));
        log.info("Task [" + task.getTitle() + "] in case [" + useCase.getTitle() + "] assigned to [" + user.getEmail() + "] was cancelled");
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
                dataService.runActions(transition.getPreCancelActions(), useCase.getStringId(), transition);
                returnTokens(task, useCase.getStringId());
                dataService.runActions(transition.getPostCancelActions(), useCase.getStringId(), transition);
            }
        }
    }

    private Task returnTokens(Task task, String useCaseId) {
        Case useCase = workflowService.findOne(useCaseId);
        PetriNet net = useCase.getPetriNet();
        net.getArcsOfTransition(task.getTransitionId()).stream()
                .filter(arc -> arc.getSource() instanceof Place)
                .forEach(arc -> {
                    if (arc instanceof ResetArc) {
                        ((ResetArc) arc).setRemovedTokens(useCase.getResetArcTokens().get(arc.getStringId()));
                        useCase.getResetArcTokens().remove(arc.getStringId());
                    }
                    arc.rollbackExecution();
                });
        workflowService.updateMarking(useCase);

        task.setUserId(null);
        task.setStartDate(null);
        task = taskRepository.save(task);
        workflowService.save(useCase);

        return task;
    }

    @Override
    @Transactional
    public EventOutcome delegateTask(LoggedUser loggedUser, Long delegatedId, String taskId) throws TransitionNotExecutableException {
        User delegatedUser = userService.findById(delegatedId, true);
        User delegateUser = userService.findById(loggedUser.getId(), true);
        Task task = taskRepository.findOne(taskId);
        Case useCase = workflowService.findOne(task.getCaseId());
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());
        EventOutcome outcome = new EventOutcome(transition.getDelegateMessage());

        outcome.add(dataService.runActions(transition.getPreDelegateActions(), useCase.getStringId(), transition));
        delegate(delegatedUser, task, useCase);
        outcome.add(dataService.runActions(transition.getPostDelegateActions(), useCase.getStringId(), transition));
        useCase = workflowService.findOne(useCase.getStringId());
        reloadTasks(useCase);

        publisher.publishEvent(new UserDelegateTaskEvent(delegateUser, task, useCase, delegatedUser));
        log.info("Task [" + task.getTitle() + "] in case [" + useCase.getTitle() + "] assigned to [" + delegateUser.getEmail() + "] was delegated to [" + delegatedUser.getEmail() + "]");
        return outcome;
    }

    protected void delegate(User delegated, Task task, Case useCase) throws TransitionNotExecutableException {
        if (task.getUserId() != null) {
            task.setUserId(delegated.getId());
            taskRepository.save(task);
        } else {
            assignTaskToUser(delegated, task, useCase.getStringId());
        }
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
        log.info("Reloading tasks in [" + useCase.getTitle() + "]");
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
        List<Task> tasks = taskRepository.findAllByCaseId(useCase.getStringId());
        if (tasks.stream().anyMatch(task -> Objects.equals(task.getUserId(), userService.getSystem().getId()) && task.getStartDate() != null)) {
            return;
        }
        for (Task task : tasks) {
            if (Objects.equals(task.getUserId(), userService.getSystem().getId()) && task.getStartDate() == null) {
                executeTransition(task, workflowService.findOne(useCase.getStringId()));
                return;
            }
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
        log.info("Finish execution of " + transition.getTitle() + " in case " + useCase.getTitle());
        execute(transition, useCase, arc -> arc.getSource().equals(transition));
        useCase.getPetriNet().getArcsOfTransition(transition.getStringId()).stream()
                .filter(arc -> arc instanceof ResetArc)
                .forEach(arc -> useCase.getResetArcTokens().remove(arc.getStringId()));
        workflowService.save(useCase);
    }

    @Transactional
    public void startExecution(Transition transition, Case useCase) throws TransitionNotExecutableException {
        log.info("Start execution of " + transition.getTitle() + " in case " + useCase.getTitle());
        execute(transition, useCase, arc -> arc.getDestination().equals(transition));
    }

    @Transactional
    protected void execute(Transition transition, Case useCase, Predicate<Arc> predicate) throws TransitionNotExecutableException {
        Supplier<Stream<Arc>> filteredSupplier = () -> useCase.getPetriNet().getArcsOfTransition(transition.getStringId()).stream().filter(predicate);

        if (!filteredSupplier.get().allMatch(Arc::isExecutable))
            throw new TransitionNotExecutableException("Not all arcs can be executed task [" + transition.getStringId() + "] in case [" + useCase.getTitle() + "]");

        filteredSupplier.get().forEach(arc -> {
            if (arc instanceof ResetArc) {
                useCase.getResetArcTokens().put(arc.getStringId(), ((Place) arc.getSource()).getTokens());
            }
            arc.execute();
        });

        workflowService.updateMarking(useCase);
    }

    @Transactional
    protected EventOutcome executeTransition(Task task, Case useCase) {
        log.info("executeTransition [" + task.getTransitionId() + "] in case [" + useCase.getTitle() + "]");
        useCase = workflowService.decrypt(useCase);
        EventOutcome outcome = new EventOutcome();
        try {
            log.info("assignTask [" + task.getTitle() + "] in case [" + useCase.getTitle() + "]");
            assignTask(task.getStringId());
            log.info("getData [" + task.getTitle() + "] in case [" + useCase.getTitle() + "]");
            dataService.getData(task.getStringId());
            log.info("finishTask [" + task.getTitle() + "] in case [" + useCase.getTitle() + "]");
            finishTask(task.getStringId());

            outcome.setMessage(new I18nString("Task " + task.getTitle() + " executed"));
        } catch (TransitionNotExecutableException e) {
            log.error("execution of task [" + task.getTitle() + "] in case [" + useCase.getTitle() + "] failed");
            e.printStackTrace();
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
        log.info("Task " + task.getTitle() + " scheduled to run at " + time.toString());
        scheduler.schedule(() -> {
            try {
                executeTransition(task, useCase);
            } catch (Exception e) {
                log.info("Scheduled task [" + task.getTitle() + "] of case [" + useCase.getTitle() + "] could not be executed: " + e);
            }
        }, DateUtils.localDateTimeToDate(time));
        publisher.publishEvent(new TimeFinishTaskEvent(time, task, useCase));
    }

    @Override
    public Task findOne(String taskId) {
        return taskRepository.findOne(taskId);
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
    public Page<Task> search(Map<String, Object> request, Pageable pageable, LoggedUser user) {
        if (request.containsKey("or")) {
            if (((Map<String, Object>) request.get("or")).containsKey("role")) {
                Object roles = ((Map<String, Object>) request.get("or")).get("role");
                Set<String> union = new HashSet<>(user.getProcessRoles());
                if (roles instanceof String)
                    union.add((String) roles);
                else if (roles instanceof List)
                    union.addAll((List) roles);

                ((Map<String, Object>) request.get("or")).put("role", new ArrayList<>(union));

            } else
                ((Map<String, Object>) request.get("or")).put("role", new ArrayList<>(user.getProcessRoles()));

        } else {
            Map<String, Object> orMap = new LinkedHashMap<>();
            orMap.put("role", new ArrayList<>(user.getProcessRoles()));
            request.put("or", orMap);
        }

        Page<Task> page = loadUsers(searchService.search(request, pageable, Task.class));
        return dataService.setImmediateFields(page);
    }

    @Override
    public Page<Task> findByCases(Pageable pageable, List<String> cases) {
        return loadUsers(taskRepository.findByCaseIdIn(pageable, cases));
    }

    @Override
    public Task findById(String id) {
        Task task = taskRepository.findOne(id);
        if (task.getUserId() != null)
            task.setUser(userService.findById(task.getUserId(), true));
        return task;
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
                taskRepository.save(task);
            }
        }
    }

    @Override
    public Page<Task> findByUser(Pageable pageable, User user) {
        return loadUsers(taskRepository.findByUserId(pageable, user.getId()));
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
    public Task searchOne(com.querydsl.core.types.Predicate predicate) {
        Page<Task> tasks = taskRepository.findAll(predicate, new PageRequest(0, 1));
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

    private Task createFromTransition(Transition transition, Case useCase) {
        final Task task = Task.with()
                .title(transition.getTitle())
                .processId(useCase.getPetriNetId())
                .caseId(useCase.get_id().toString())
                .transitionId(transition.getImportId())
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
        for (Trigger trigger : transition.getTriggers()) {
            Trigger taskTrigger = trigger.clone();
            task.addTrigger(taskTrigger);

            if (taskTrigger instanceof TimeTrigger) {
                TimeTrigger timeTrigger = (TimeTrigger) taskTrigger;
                scheduleTaskExecution(task, timeTrigger.getStartDate(), useCase);
            } else if (taskTrigger instanceof AutoTrigger) {
                task.setUserId(userService.getSystem().getId());
            }
        }
        for (Map.Entry<String, Set<RolePermission>> entry : transition.getRoles().entrySet()) {
            task.addRole(entry.getKey(), entry.getValue());
        }

        Transaction transaction = useCase.getPetriNet().getTransactionByTransition(transition);
        if (transaction != null) {
            task.setTransactionId(transaction.getStringId());
        }
        Task savedTask = taskRepository.save(task);

        useCase.addTask(savedTask);
        useCase = workflowService.save(useCase);

        publisher.publishEvent(new CreateTaskEvent(savedTask, useCase));

        return savedTask;
    }

    private Page<Task> loadUsers(Page<Task> tasks) {
        Map<Long, User> users = new HashMap<>();
        tasks.forEach(task -> {
            if (task.getUserId() != null) {
                if (users.containsKey(task.getUserId()))
                    task.setUser(users.get(task.getUserId()));
                else {
                    task.setUser(userService.findById(task.getUserId(), true));
                    users.put(task.getUserId(), task.getUser());
                }
            }
        });

        return tasks;
    }

    @Override
    public void delete(Iterable<? extends Task> tasks, Case useCase) {
        workflowService.removeTasksFromCase(tasks, useCase);
        taskRepository.delete(tasks);
    }

    @Override
    public void delete(Iterable<? extends Task> tasks, String caseId) {
        workflowService.removeTasksFromCase(tasks, caseId);
        taskRepository.delete(tasks);
    }

    @Override
    public void deleteTasksByCase(String caseId) {
        delete(taskRepository.findAllByCaseId(caseId), caseId);
    }
}