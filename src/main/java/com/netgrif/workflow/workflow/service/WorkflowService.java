package com.netgrif.workflow.workflow.service;

import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.service.interfaces.IUserService;
import com.netgrif.workflow.elastic.service.interfaces.IElasticCaseMappingService;
import com.netgrif.workflow.elastic.service.interfaces.IElasticCaseService;
import com.netgrif.workflow.history.domain.caseevents.CreateCaseEventLog;
import com.netgrif.workflow.history.domain.caseevents.DeleteCaseEventLog;
import com.netgrif.workflow.history.service.IHistoryService;
import com.netgrif.workflow.importer.service.FieldFactory;
import com.netgrif.workflow.petrinet.domain.I18nString;
import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.dataset.Field;
import com.netgrif.workflow.petrinet.domain.dataset.FieldType;
import com.netgrif.workflow.petrinet.domain.dataset.TaskField;
import com.netgrif.workflow.petrinet.domain.dataset.logic.action.FieldActionsRunner;
import com.netgrif.workflow.petrinet.domain.events.CaseEventType;
import com.netgrif.workflow.petrinet.domain.events.EventPhase;
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService;
import com.netgrif.workflow.petrinet.service.interfaces.IProcessRoleService;
import com.netgrif.workflow.rules.domain.facts.CaseCreatedFact;
import com.netgrif.workflow.rules.service.interfaces.IRuleEngine;
import com.netgrif.workflow.security.service.EncryptionService;
import com.netgrif.workflow.utils.FullPageRequest;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.DataField;
import com.netgrif.workflow.workflow.domain.Task;
import com.netgrif.workflow.workflow.domain.TaskPair;
import com.netgrif.workflow.workflow.domain.eventoutcomes.EventOutcome;
import com.netgrif.workflow.workflow.domain.eventoutcomes.caseoutcomes.CreateCaseEventOutcome;
import com.netgrif.workflow.workflow.domain.eventoutcomes.caseoutcomes.DeleteCaseEventOutcome;
import com.netgrif.workflow.workflow.domain.repositories.CaseRepository;
import com.netgrif.workflow.workflow.service.interfaces.IEventService;
import com.netgrif.workflow.workflow.service.interfaces.IInitValueExpressionEvaluator;
import com.netgrif.workflow.workflow.service.interfaces.ITaskService;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService;
import com.querydsl.core.types.Predicate;
import org.apache.commons.collections.map.HashedMap;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

@Service
public class WorkflowService implements IWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    @Autowired
    protected CaseRepository repository;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected IPetriNetService petriNetService;

    @Autowired
    protected IProcessRoleService processRoleService;

    @Autowired
    protected ITaskService taskService;

    @Autowired
    protected CaseSearchService searchService;

    @Autowired
    protected ApplicationEventPublisher publisher;

    @Autowired
    protected EncryptionService encryptionService;

    @Autowired
    protected FieldFactory fieldFactory;

    @Autowired
    protected IRuleEngine ruleEngine;

    @Autowired
    protected FieldActionsRunner actionsRunner;

    @Autowired
    protected IUserService userService;

    @Autowired
    protected IInitValueExpressionEvaluator initValueExpressionEvaluator;

    @Autowired
    protected IElasticCaseMappingService caseMappingService;

    @Lazy
    @Autowired
    private IEventService eventService;

    @Autowired
    private IHistoryService historyService;

    protected IElasticCaseService elasticCaseService;

    @Autowired
    public void setElasticCaseService(IElasticCaseService elasticCaseService) {
        this.elasticCaseService = elasticCaseService;
    }

    @Override
    public Case save(Case useCase) {
        if (useCase.getPetriNet() == null) {
            setPetriNet(useCase);
        }
        encryptDataSet(useCase);
        useCase = repository.save(useCase);
        resolveUserRef(useCase);
        taskService.resolveUserRef(useCase);
        try {
            setImmediateDataFields(useCase);
            elasticCaseService.indexNow(this.caseMappingService.transform(useCase));
        } catch (Exception e) {
            log.error("Indexing failed [" + useCase.getStringId() + "]", e);
        }
        return useCase;
    }

    @Override
    public Case findOne(String caseId) {
        Optional<Case> caseOptional = repository.findById(caseId);
        if (!caseOptional.isPresent())
            throw new IllegalArgumentException("Could not find Case with id [" + caseId + "]");
        Case useCase = caseOptional.get();
        setPetriNet(useCase);
        decryptDataSet(useCase);
        this.setImmediateDataFieldsReadOnly(useCase);
        return useCase;
    }

    @Override
    public List<Case> findAllById(List<String> ids) {
        List<Case> page = new LinkedList<>();
        ids.forEach(id -> {
            Optional<Case> useCase = repository.findById(id);
            useCase.ifPresent(page::add);
        });
        if (page.size() > 0) {
            page.forEach(c -> c.setPetriNet(petriNetService.get(c.getPetriNetObjectId())));
            decryptDataSets(page);
            page.forEach(this::setImmediateDataFieldsReadOnly);
        }
        return page;
    }

    @Override
    public Page<Case> getAll(Pageable pageable) {
        Page<Case> page = repository.findAll(pageable);
        page.getContent().forEach(this::setPetriNet);
        decryptDataSets(page.getContent());
        return setImmediateDataFields(page);
    }

    @Override
    public Page<Case> search(Predicate predicate, Pageable pageable) {
        Page<Case> page = repository.findAll(predicate, pageable);
        page.getContent().forEach(this::setPetriNet);
        return setImmediateDataFields(page);
    }

    @Override
    public Page<Case> search(Map<String, Object> request, Pageable pageable, LoggedUser user, Locale locale) {
        Predicate searchPredicate = searchService.buildQuery(request, user, locale);
        Page<Case> page;
        if (searchPredicate != null) {
            page = repository.findAll(searchPredicate, pageable);
        } else {
            page = Page.empty();
        }
        page.getContent().forEach(this::setPetriNet);
        decryptDataSets(page.getContent());
        return setImmediateDataFields(page);
    }

    @Override
    public long count(Map<String, Object> request, LoggedUser user, Locale locale) {
        Predicate searchPredicate = searchService.buildQuery(request, user, locale);
        if (searchPredicate != null) {
            return repository.count(searchPredicate);
        } else {
            return 0;
        }
    }

    @Override
    public Case resolveUserRef(Case useCase) {
        useCase.getUsers().clear();
        useCase.getNegativeViewUsers().clear();
        useCase.getUserRefs().forEach((id, permission) -> {
            List<String> userIds = getExistingUsers((List<String>) useCase.getDataSet().get(id).getValue());
            if (userIds != null && userIds.size() != 0 && permission.containsKey("view") && !permission.get("view")) {
                useCase.getNegativeViewUsers().addAll(userIds);
            } else if (userIds != null && userIds.size() != 0) {
                useCase.addUsers(new HashSet<>(userIds), permission);
            }
        });
        return repository.save(useCase);
    }

    private List<String> getExistingUsers(List<String> userIds) {
        if (userIds == null)
            return null;
        return userIds.stream().filter(userId -> userService.resolveById(userId, false) != null).collect(Collectors.toList());
    }

    @Override
    public CreateCaseEventOutcome createCase(String netId, String title, String color, LoggedUser user, Locale locale) {
        if (locale == null) {
            locale = LocaleContextHolder.getLocale();
        }
        if (title == null) {
            return this.createCase(netId, resolveDefaultCaseTitle(netId, locale), color, user);
        }
        return this.createCase(netId, title, color, user);
    }

    @Override
    public CreateCaseEventOutcome createCase(String netId, String title, String color, LoggedUser user) {
        return createCase(netId, (u) -> title, color, user);
    }

    @Override
    public CreateCaseEventOutcome createCaseByIdentifier(String identifier, String title, String color, LoggedUser user, Locale locale) {
        PetriNet net = petriNetService.getNewestVersionByIdentifier(identifier);
        if (net == null) {
            throw new IllegalArgumentException("Petri net with identifier [" + identifier + "] does not exist.");
        }
        return this.createCase(net.getStringId(), title != null && !title.equals("") ? title : net.getDefaultCaseName().getTranslation(locale), color, user);
    }

    @Override
    public CreateCaseEventOutcome createCaseByIdentifier(String identifier, String title, String color, LoggedUser user) {
        PetriNet net = petriNetService.getNewestVersionByIdentifier(identifier);
        if (net == null) {
            throw new IllegalArgumentException("Petri net with identifier [" + identifier + "] does not exist.");
        }
        return this.createCase(net.getStringId(), title, color, user);
    }

    public CreateCaseEventOutcome createCase(String netId, Function<Case, String> makeTitle, String color, LoggedUser user) {
        PetriNet petriNet = petriNetService.clone(new ObjectId(netId));
        Case useCase = new Case(petriNet, petriNet.getActivePlaces());
        useCase.populateDataSet(initValueExpressionEvaluator);
        useCase.setProcessIdentifier(petriNet.getIdentifier());
        useCase.setColor(color);
        useCase.setAuthor(user.transformToAuthor());
        useCase.setIcon(petriNet.getIcon());
        useCase.setCreationDate(LocalDateTime.now());
        useCase.setPermissions(petriNet.getPermissions().entrySet().stream()
                .filter(role -> role.getValue().containsKey("delete") || role.getValue().containsKey("view"))
                .map(role -> {
                    Map<String, Boolean> permissionMap = new HashMap<>();
                    if (role.getValue().containsKey("delete"))
                        permissionMap.put("delete", role.getValue().get("delete"));
                    if (role.getValue().containsKey("view")) {
                        permissionMap.put("view", role.getValue().get("view"));
                    }
                    return new AbstractMap.SimpleEntry<>(role.getKey(), permissionMap);
                })
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue))
        );
        useCase.addNegativeViewRoles(petriNet.getNegativeViewRoles());
        useCase.setUserRefs(petriNet.getUserRefs());
        useCase.decideEnabledRoles(petriNet);
        setDefaultRoleIfEnabled(petriNet, useCase);

        useCase.setTitle(makeTitle.apply(useCase));
        CreateCaseEventOutcome outcome = new CreateCaseEventOutcome();
        outcome.addOutcomes(eventService.runActions(petriNet.getPreCreateActions(), null, Optional.empty() ));
        ruleEngine.evaluateRules(useCase, new CaseCreatedFact(useCase.getStringId(), EventPhase.PRE));
        useCase = save(useCase);

        historyService.save(new CreateCaseEventLog(useCase, EventPhase.PRE));
        log.info("[" + useCase.getStringId() + "]: Case " + useCase.getTitle() + " created");

        useCase.getPetriNet().initializeArcs(useCase.getDataSet());
        taskService.reloadTasks(useCase);
        useCase = findOne(useCase.getStringId());
        resolveTaskRefs(useCase);

        useCase = findOne(useCase.getStringId());
        outcome.addOutcomes(eventService.runActions(petriNet.getPostCreateActions(), useCase, Optional.empty()));
        useCase = findOne(useCase.getStringId());
        ruleEngine.evaluateRules(useCase, new CaseCreatedFact(useCase.getStringId(), EventPhase.POST));
        useCase = save(useCase);
        historyService.save(new CreateCaseEventLog(useCase, EventPhase.POST));
        outcome.setCase(setImmediateDataFields(useCase));
        addMessageToOutcome(petriNet, CaseEventType.CREATE, outcome);
        return outcome;
    }

    protected Function<Case, String> resolveDefaultCaseTitle(String netId, Locale locale) {
        PetriNet petriNet = petriNetService.clone(new ObjectId(netId));
        Function<Case, String> makeTitle;
        if (petriNet.hasDynamicCaseName()) {
            makeTitle = (u) -> initValueExpressionEvaluator.evaluateCaseName(u, petriNet.getDefaultCaseNameExpression()).getTranslation(locale);
        } else {
            makeTitle = (u) -> petriNet.getDefaultCaseName().getTranslation(locale);
        }
        return makeTitle;
    }

    @Override
    public Page<Case> findAllByAuthor(String authorId, String petriNet, Pageable pageable) {
        String queryString = "{author.id:" + authorId + ", petriNet:{$ref:\"petriNet\",$id:{$oid:\"" + petriNet + "\"}}}";
        BasicQuery query = new BasicQuery(queryString);
        query = (BasicQuery) query.with(pageable);
        List<Case> cases = mongoTemplate.find(query, Case.class);
        decryptDataSets(cases);
        return setImmediateDataFields(new PageImpl<Case>(cases, pageable, mongoTemplate.count(new BasicQuery(queryString, "{_id:1}"), Case.class)));
    }

    @Override
    public DeleteCaseEventOutcome deleteCase(String caseId) {
        Case useCase = findOne(caseId);

        DeleteCaseEventOutcome outcome = new DeleteCaseEventOutcome(useCase, eventService.runActions(useCase.getPetriNet().getPreDeleteActions(), useCase, Optional.empty()));
        historyService.save(new DeleteCaseEventLog(useCase, EventPhase.PRE));
        log.info("[" + caseId + "]: Deleting case " + useCase.getTitle());

        taskService.deleteTasksByCase(caseId);
        repository.delete(useCase);

        outcome.addOutcomes(eventService.runActions(useCase.getPetriNet().getPostDeleteActions(), null, Optional.empty()));
        addMessageToOutcome(petriNetService.clone(useCase.getPetriNetObjectId()), CaseEventType.DELETE, outcome);
        historyService.save(new DeleteCaseEventLog(useCase, EventPhase.POST));
        return outcome;
    }

    @Override
    public void deleteInstancesOfPetriNet(PetriNet net) {
        log.info("[" + net.getStringId() + "]: Deleting all cases of Petri net " + net.getIdentifier() + " version " + net.getVersion().toString());

        taskService.deleteTasksByPetriNetId(net.getStringId());
        repository.deleteAllByPetriNetObjectId(net.getObjectId());
    }

    @Override
    public DeleteCaseEventOutcome deleteSubtreeRootedAt(String subtreeRootCaseId) {
        Case subtreeRoot = findOne(subtreeRootCaseId);
        if (subtreeRoot.getImmediateDataFields().contains("treeChildCases")) {
            ((List<String>) subtreeRoot.getDataSet().get("treeChildCases").getValue()).forEach(this::deleteSubtreeRootedAt);
        }
        return deleteCase(subtreeRootCaseId);
    }

    @Override
    public void updateMarking(Case useCase) {
        PetriNet net = useCase.getPetriNet();
        useCase.setActivePlaces(net.getActivePlaces());
    }

    @Override
    public boolean removeTasksFromCase(Iterable<? extends Task> tasks, String caseId) {
        Optional<Case> caseOptional = repository.findById(caseId);
        if (!caseOptional.isPresent())
            throw new IllegalArgumentException("Could not find case with id [" + caseId + "]");
        Case useCase = caseOptional.get();
        return removeTasksFromCase(tasks, useCase);
    }

    @Override
    public boolean removeTasksFromCase(Iterable<? extends Task> tasks, Case useCase) {
        if (StreamSupport.stream(tasks.spliterator(), false).count() == 0) {
            return true;
        }
        boolean deleteSuccess = useCase.removeTasks(StreamSupport.stream(tasks.spliterator(), false).collect(Collectors.toList()));
        save(useCase);
        return deleteSuccess;
    }

    @Override
    public Case decrypt(Case useCase) {
        decryptDataSet(useCase);
        return useCase;
    }

    @Override
    public Page<Case> searchAll(Predicate predicate) {
        return search(predicate, new FullPageRequest());
    }

    @Override
    public Case searchOne(Predicate predicate) {
        Page<Case> page = search(predicate, PageRequest.of(0, 1));
        if (page.getContent().isEmpty())
            return null;
        return page.getContent().get(0);
    }

    @Override
    public Map<String, I18nString> listToMap(List<Case> cases) {
        Map<String, I18nString> options = new HashMap<>();
        cases.forEach(aCase -> options.put(aCase.getStringId(), new I18nString(aCase.getTitle())));
        return options;
    }

    private void resolveTaskRefs(Case useCase) {
        useCase.getPetriNet().getDataSet().values().stream().filter(f -> f instanceof TaskField).map(TaskField.class::cast).forEach(field -> {
            if (field.getDefaultValue() != null && !field.getDefaultValue().isEmpty() && useCase.getDataField(field.getStringId()).getValue() != null &&
                    useCase.getDataField(field.getStringId()).getValue().equals(field.getDefaultValue())) {
                useCase.getDataField(field.getStringId()).setValue(new ArrayList<>());
                List<TaskPair> taskPairList = useCase.getTasks().stream().filter(t ->
                        (field.getDefaultValue().contains(t.getTransition()))).collect(Collectors.toList());
                if (!taskPairList.isEmpty()) {
                    taskPairList.forEach(pair -> ((List<String>) useCase.getDataField(field.getStringId()).getValue()).add(pair.getTask()));
                }
            }
        });
        save(useCase);
    }

    private void setDefaultRoleIfEnabled(PetriNet net, Case useCase) {
        if (useCase.getViewUserRefs().isEmpty() && useCase.getViewRoles().isEmpty() && net.isDefaultRoleEnabled()) {
            useCase.addAllRolesToViewRoles(processRoleService.defaultRole().getStringId());
        }
    }

    private void setImmediateDataFieldsReadOnly(Case useCase) {
        List<Field> immediateData = new ArrayList<>();

        useCase.getImmediateDataFields().forEach(fieldId -> {
            try {
                Field field = fieldFactory.buildImmediateField(useCase, fieldId);
                Field clone = field.clone();
                if (field.getValue() != null) {
                    if (field.getType() == FieldType.TEXT) {
                        clone.setValue(field.getValue().toString());
                    } else {
                        clone.setValue(field.getValue());
                    }
                } else {
                    clone.setValue(null);
                }
                immediateData.add(clone);
            } catch (Exception e) {
                log.error("Could not built immediate field [" + fieldId + "]");
            }
        });
        LongStream.range(0L, immediateData.size()).forEach(index -> immediateData.get((int) index).setOrder(index));

        useCase.setImmediateData(immediateData);
    }

    protected Page<Case> setImmediateDataFields(Page<Case> cases) {
        cases.getContent().forEach(this::setImmediateDataFields);
        return cases;
    }

    protected Case setImmediateDataFields(Case useCase) {
        List<Field> immediateData = new ArrayList<>();

        useCase.getImmediateDataFields().forEach(fieldId ->
                immediateData.add(fieldFactory.buildImmediateField(useCase, fieldId))
        );
        LongStream.range(0L, immediateData.size()).forEach(index -> immediateData.get((int) index).setOrder(index));

        useCase.setImmediateData(immediateData);
        return useCase;
    }

    private void encryptDataSet(Case useCase) {
        applyCryptoMethodOnDataSet(useCase, entry -> encryptionService.encrypt(entry.getFirst(), entry.getSecond()));
    }

    private void decryptDataSet(Case useCase) {
        applyCryptoMethodOnDataSet(useCase, entry -> encryptionService.decrypt(entry.getFirst(), entry.getSecond()));
    }

    private void decryptDataSets(Collection<Case> cases) {
        for (Case aCase : cases) {
            decryptDataSet(aCase);
        }
    }

    private void applyCryptoMethodOnDataSet(Case useCase, Function<Pair<String, String>, String> method) {
        Map<DataField, String> dataFields = getEncryptedDataSet(useCase);

        for (Map.Entry<DataField, String> entry : dataFields.entrySet()) {
            DataField dataField = entry.getKey();
            String value = (String) dataField.getValue();
            String encryption = entry.getValue();

            if (value == null)
                continue;

            dataField.setValue(method.apply(Pair.of(value, encryption)));
        }
    }

    private Map<DataField, String> getEncryptedDataSet(Case useCase) {
        PetriNet net = useCase.getPetriNet();
        Map<DataField, String> encryptedDataSet = new HashMap<>();

        for (Map.Entry<String, Field> entry : net.getDataSet().entrySet()) {
            String encryption = entry.getValue().getEncryption();
            if (encryption != null) {
                encryptedDataSet.put(useCase.getDataSet().get(entry.getKey()), encryption);
            }
        }

        return encryptedDataSet;
    }

    private void setPetriNet(Case useCase) {
        PetriNet model = petriNetService.clone(useCase.getPetriNetObjectId());
        model.initializeTokens(useCase.getActivePlaces());
        model.initializeArcs(useCase.getDataSet());
        useCase.setPetriNet(model);
    }

    private EventOutcome addMessageToOutcome(PetriNet net, CaseEventType type, EventOutcome outcome) {
        if(net.getCaseEvents().containsKey(type)){
            outcome.setMessage(net.getCaseEvents().get(type).getMessage());
        }
        return outcome;
    }
}