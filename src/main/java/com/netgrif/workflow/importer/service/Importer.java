package com.netgrif.workflow.importer.service;

import com.netgrif.workflow.importer.model.*;
import com.netgrif.workflow.petrinet.domain.DataGroup;
import com.netgrif.workflow.petrinet.domain.Event;
import com.netgrif.workflow.petrinet.domain.EventType;
import com.netgrif.workflow.petrinet.domain.*;
import com.netgrif.workflow.petrinet.domain.Place;
import com.netgrif.workflow.petrinet.domain.Transaction;
import com.netgrif.workflow.petrinet.domain.Transition;
import com.netgrif.workflow.petrinet.domain.arcs.Arc;
import com.netgrif.workflow.petrinet.domain.dataset.Field;
import com.netgrif.workflow.petrinet.domain.dataset.logic.FieldBehavior;
import com.netgrif.workflow.petrinet.domain.dataset.logic.action.Action;
import com.netgrif.workflow.petrinet.domain.policies.AssignPolicy;
import com.netgrif.workflow.petrinet.domain.policies.DataFocusPolicy;
import com.netgrif.workflow.petrinet.domain.policies.FinishPolicy;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRole;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRoleRepository;
import com.netgrif.workflow.petrinet.service.ArcFactory;
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService;
import com.netgrif.workflow.workflow.domain.triggers.Trigger;
import lombok.Getter;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class Importer {

    public static final String ARCHIVED_FILES_PATH = "storage/uploadedModels/";
    public static final String FILE_EXTENSION = ".xml";

    public static final String FIELD_KEYWORD = "f";
    public static final String TRANSITION_KEYWORD = "t";

    private Config config;
    private Document document;
    private PetriNet net;
    private ProcessRole defaultRole;
    @Getter
    private Map<String, ProcessRole> roles;
    private Map<String, Field> fields;
    private Map<String, Transition> transitions;
    private Map<String, Place> places;
    private Map<String, Transaction> transactions;
    private Map<String, I18nString> i18n;

    @Autowired
    private FieldFactory fieldFactory;

    @Autowired
    private IPetriNetService service;

    @Autowired
    private ProcessRoleRepository roleRepository;

    @Autowired
    private ArcFactory arcFactory;

    @Autowired
    private RoleFactory roleFactory;

    @Autowired
    private TriggerFactory triggerFactory;

    @Transactional
    public Optional<PetriNet> importPetriNet(InputStream xml, String title, String initials, Config config) {
        try {
            initialize(config);
            unmarshallXml(xml);
            return createPetriNet(title, initials);
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<PetriNet> importPetriNet(File xml, String title, String initials, Config config) {
        try {
            return importPetriNet(new FileInputStream(xml), title, initials, config);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<PetriNet> importPetriNet(InputStream xml, String title, String initials) {
        return importPetriNet(xml, title, initials, new Config());
    }

    @Transactional
    public Optional<PetriNet> importPetriNet(File xml, String title, String initials) {
        return importPetriNet(xml, title, initials, new Config());
    }

    private void initialize(Config config) {
        this.config = config;
        this.roles = new HashMap<>();
        this.transitions = new HashMap<>();
        this.places = new HashMap<>();
        this.fields = new HashMap<>();
        this.transactions = new HashMap<>();
        this.defaultRole = roleRepository.findByName_DefaultValue(ProcessRole.DEFAULT_ROLE);
        this.i18n = new HashMap<>();
    }

    @Transactional
    protected void unmarshallXml(InputStream xml) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(Document.class);

        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        document = (Document) jaxbUnmarshaller.unmarshal(xml);
    }

    @Transactional
    public Path saveNetFile(PetriNet net, File xmlFile) throws IOException {
        File savedFile = new File(ARCHIVED_FILES_PATH + net.getStringId() + "-" + net.getTitle() + FILE_EXTENSION);
        savedFile.getParentFile().mkdirs();
        net.setImportXmlPath(savedFile.getPath());
        return Files.copy(xmlFile.toPath(), savedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @Transactional
    protected Optional<PetriNet> createPetriNet(String title, String initials) {
        net = new PetriNet();
        net.setImportId(document.getId() != null ? document.getId() : new ObjectId().toString());
        net.setIcon(document.getIcon());

        document.getI18N().forEach(this::addI18N);
        document.getRole().forEach(this::createRole);
        document.getData().forEach(this::createDataSet);
        document.getTransaction().forEach(this::createTransaction);
        document.getPlace().forEach(this::createPlace);
        document.getTransition().forEach(this::createTransition);
        document.getArc().forEach(this::createArc);
        document.getMapping().forEach(this::applyMapping);
        document.getTransition().forEach(this::resolveTransitionActions);
        document.getData().forEach(this::resolveDataActions);

        net.setTitle(title != null ? new I18nString(title) : toI18NString(document.getTitle()));
        net.setInitials(initials != null ? initials : document.getInitials());
        net.setDefaultCaseName(toI18NString(document.getCaseName()));
        net.setIdentifier(document.getId());

        if (config.isNotSaveObjects())
            return service.saveNew(net);
        else
            return Optional.of(net);
    }

    @Transactional
    protected void addI18N(I18N importI18N) {
        String locale = importI18N.getLocale();
        importI18N.getI18NString().forEach(translation -> addTranslation(translation, locale));
    }

    @Transactional
    protected void addTranslation(I18NStringType i18NStringType, String locale) {
        String name = i18NStringType.getName();
        I18nString translation = getI18n(name);
        if (translation == null) {
            translation = new I18nString();
            i18n.put(name, translation);
        }
        translation.addTranslation(locale, i18NStringType.getValue());
    }

    @Transactional
    protected void applyMapping(Mapping mapping) {
        Transition transition = getTransition(mapping.getTransitionRef());
        mapping.getRoleRef().forEach(roleRef -> addRoleLogic(transition, roleRef));
        mapping.getDataRef().forEach(dataRef -> addDataLogic(transition, dataRef));
        mapping.getDataGroup().forEach(dataGroup -> addDataGroup(transition, dataGroup));
        mapping.getTrigger().forEach(trigger -> addTrigger(transition, trigger));
    }

    @Transactional
    protected void resolveDataActions(Data data) {
        if (data.getAction() != null) {
            getField(data.getId()).setActions(buildActions(data.getAction(), getField(data.getId()).getStringId(), null));
        }
    }

    @Transactional
    protected void resolveTransitionActions(com.netgrif.workflow.importer.model.Transition trans) {
        if (trans.getDataRef() != null) {
            resolveDataRefActions(trans.getDataRef(), trans);
        }
        if (trans.getDataGroup() != null) {
            trans.getDataGroup().forEach(ref -> {
                if (ref.getDataRef() != null) {
                    resolveDataRefActions(ref.getDataRef(), trans);
                }
            });
        }
    }

    @Transactional
    protected void resolveDataRefActions(List<DataRef> dataRef, com.netgrif.workflow.importer.model.Transition trans) {
        dataRef.forEach(ref -> {
            if (ref.getLogic().getAction() != null) {
                String fieldId = getField(ref.getId()).getStringId();
                getTransition(trans.getId()).addActions(fieldId, buildActions(ref.getLogic().getAction(),
                        fieldId,
                        getTransition(trans.getId()).getStringId()));
            }
        });
    }

    @Transactional
    protected void createArc(com.netgrif.workflow.importer.model.Arc importArc) {
        Arc arc = arcFactory.getArc(importArc);
        arc.setImportId(importArc.getId());
        arc.setMultiplicity(importArc.getMultiplicity());
        arc.setSource(getNode(importArc.getSourceId()));
        arc.setDestination(getNode(importArc.getDestinationId()));

        net.addArc(arc);
    }

    @Transactional
    protected void createDataSet(Data importData) {
        Field field = fieldFactory.getField(importData, this);

        net.addDataSetField(field);
        fields.put(importData.getId(), field);
    }

    @Transactional
    protected void createTransition(com.netgrif.workflow.importer.model.Transition importTransition) {
        Transition transition = new Transition();
        transition.setImportId(importTransition.getId());
        transition.setTitle(toI18NString(importTransition.getLabel()));
        transition.setPosition(importTransition.getX(), importTransition.getY());
        transition.setPriority(importTransition.getPriority());
        transition.setIcon(importTransition.getIcon());
        transition.setAssignPolicy(toAssignPolicy(importTransition.getAssignPolicy()));
        transition.setDataFocusPolicy(toDataFocusPolicy(importTransition.getDataFocusPolicy()));
        transition.setFinishPolicy(toFinishPolicy(importTransition.getFinishPolicy()));

        if (importTransition.getRoleRef() != null) {
            importTransition.getRoleRef().forEach(roleRef ->
                    addRoleLogic(transition, roleRef)
            );
        }
        if (importTransition.getDataRef() != null) {
            importTransition.getDataRef().forEach(dataRef ->
                    addDataWithDefaultGroup(transition, dataRef)
            );
        }
        if (importTransition.getTrigger() != null) {
            importTransition.getTrigger().forEach(trigger ->
                    addTrigger(transition, trigger)
            );
        }
        if (importTransition.getTransactionRef() != null) {
            addToTransaction(transition, importTransition.getTransactionRef());
        }
        if (importTransition.getDataGroup() != null) {
            importTransition.getDataGroup().forEach(dataGroup ->
                    addDataGroup(transition, dataGroup)
            );
        }
        if (isDefaultRoleAllowedFor(importTransition, document)) {
            addDefaultRole(transition);
        }
        if (importTransition.getEvent() != null) {
            importTransition.getEvent().forEach(event ->
                    addEvent(transition, event)
            );
        }

        net.addTransition(transition);
        transitions.put(importTransition.getId(), transition);
    }

    @Transactional
    protected void addEvent(Transition transition, com.netgrif.workflow.importer.model.Event imported) {
        Event event = new Event();
        event.setImportId(imported.getId());
        event.setMessage(toI18NString(imported.getMessage()));
        event.setTitle(toI18NString(imported.getTitle()));
        event.setType(EventType.valueOf(imported.getType().value().toUpperCase()));
        event.setPostActions(parsePostActions(transition, imported));
        event.setPreActions(parsePreActions(transition, imported));
        transition.addEvent(event);
    }

    private List<Action> parsePostActions(Transition transition, com.netgrif.workflow.importer.model.Event imported) {
        return parsePhaseActions(EventPhaseType.POST, transition, imported);
    }

    private List<Action> parsePreActions(Transition transition, com.netgrif.workflow.importer.model.Event imported) {
        return parsePhaseActions(EventPhaseType.PRE, transition, imported);
    }

    private List<Action> parsePhaseActions(EventPhaseType phase, Transition transition, com.netgrif.workflow.importer.model.Event imported) {
        return imported.getActions().stream()
                .filter(actions -> actions.getPhase().equals(phase))
                .map(actions -> actions.getAction().parallelStream()
                        .map(action -> parseAction(transition.getImportId(), action)))
                .flatMap(Function.identity())
                .collect(Collectors.toList());
    }

    @Transactional
    protected void addDefaultRole(Transition transition) {
        Logic logic = new Logic();
        logic.setDelegate(true);
        logic.setPerform(true);
        transition.addRole(defaultRole.getStringId(), roleFactory.getPermissions(logic));
    }

    @Transactional
    protected void addDataWithDefaultGroup(Transition transition, DataRef dataRef) {
        DataGroup dataGroup = new DataGroup();
        dataGroup.setImportId(transition.getImportId() + "_" + dataRef.getId() + "_" + System.currentTimeMillis());
        dataGroup.setAlignment("start");
        dataGroup.setStretch(true);
        dataGroup.addData(getField(dataRef.getId()).getStringId());
        transition.addDataGroup(dataGroup);

        addDataLogic(transition, dataRef);
    }

    @Transactional
    protected void addDataGroup(Transition transition, com.netgrif.workflow.importer.model.DataGroup importDataGroup) {
        String alignment = importDataGroup.getAlignment() != null ? importDataGroup.getAlignment().value() : "";
        DataGroup dataGroup = new DataGroup();
        dataGroup.setImportId(importDataGroup.getId());
        dataGroup.setTitle(toI18NString(importDataGroup.getTitle()));
        dataGroup.setAlignment(alignment);
        dataGroup.setStretch(importDataGroup.isStretch());
        importDataGroup.getDataRef().forEach(dataRef -> dataGroup.addData(getField(dataRef.getId()).getStringId()));
        transition.addDataGroup(dataGroup);

        for (DataRef dataRef: importDataGroup.getDataRef()) {
            addDataLogic(transition, dataRef);
        }
    }

    @Transactional
    protected void addToTransaction(Transition transition, TransactionRef transactionRef) {
        Transaction transaction = getTransaction(transactionRef.getId());
        if (transaction == null)
            throw new IllegalArgumentException("Referenced transaction [" + transactionRef.getId() + "] in transition [" + transition.getTitle() + "] doesn't exist.");
        transaction.addTransition(transition);
    }

    @Transactional
    protected void addRoleLogic(Transition transition, RoleRef roleRef) {
        Logic logic = roleRef.getLogic();
        String roleId = getRole(roleRef.getId()).getStringId();

        if (logic == null || roleId == null)
            return;

        transition.addRole(roleId, roleFactory.getPermissions(logic));
    }

    @Transactional
    protected void addDataLogic(Transition transition, DataRef dataRef) {
        Logic logic = dataRef.getLogic();
        try {
            String fieldId = getField(dataRef.getId()).getStringId();
            if (logic == null || fieldId == null)
                return;

            Set<FieldBehavior> behavior = new HashSet<>();
            if (logic.getBehavior() != null)
                logic.getBehavior().forEach(b -> behavior.add(FieldBehavior.fromString(b)));

            transition.addDataSet(fieldId, behavior, null);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Wrong dataRef id [" + dataRef.getId() + "] on transition [" + transition.getTitle() + "]", e);
        }
    }

    @Transactional
    protected LinkedHashSet<Action> buildActions(List<ActionType> imported, String fieldId, String transitionId) {
        return imported.stream()
                .map(action -> parseAction(fieldId, transitionId, action))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Action parseAction(String transitionId, ActionType action) {
        if (action.getValue().contains("f.this"))
            throw new IllegalArgumentException("Event action can not reference field using 'this'");
        return parseAction(null, transitionId, action);
    }

    private Action parseAction(String fieldId, String transitionId, ActionType importedAction) {
        if (fieldId != null && importedAction.getTrigger() == null)
            throw new IllegalArgumentException("Data field action [" + importedAction.getValue() + "] doesn't have trigger");
        try {
            Action action = createAction(importedAction);
            parseIds(fieldId, transitionId, importedAction, action);
            return action;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Error parsing ids of action [" + importedAction.getValue() + "]", e);
        }
    }

    private Action createAction(ActionType importedAction) {
        Action action = new Action(importedAction.getTrigger());
        if (importedAction.getId() != null)
            action.setImportId(importedAction.getId());
        else
            action.setImportId(new ObjectId().toString());
        return action;
    }

    private void parseIds(String fieldId, String transitionId, ActionType importedAction, Action action) {
        String definition = importedAction.getValue();
        action.setDefinition(definition);

        if (containsParams(definition)) {
            parseParamsAndObjectIds(action, fieldId, transitionId);
        }
    }

    private void parseParamsAndObjectIds(Action action, String fieldId, String transitionId) {
        String[] actionParts = action.getDefinition().split(";", 2);
        action.setDefinition(actionParts[1]);
        parseObjectIds(action, fieldId, transitionId, actionParts[0]);
    }

    private boolean containsParams(String definition) {
        return definition.matches("[\\W\\w\\s]*[\\w]*:[\\s][ft].[\\w]+;[\\w\\W\\s]*");
    }

    @Transactional
    protected void parseObjectIds(Action action, String fieldId, String transitionId, String definition) {
        try {
            Map<String, String> ids = parseParams(definition);

            ids.entrySet().forEach(entry -> replaceImportId(action, fieldId, transitionId, entry));
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Failed to parse action: " + action, e);
        }
    }

    private void replaceImportId(Action action, String fieldId, String transitionId, Map.Entry<String, String> entry) {
        String[] parts = entry.getValue().split("[.]");
        if (parts.length != 2)
            throw new IllegalArgumentException("Can not parse id of " + entry.getValue());
        String key = parts[0];
        String importId = parts[1];
        String paramName = entry.getKey().trim();

        if (importId.startsWith("this")) {
            if (Objects.equals(key.trim(), FIELD_KEYWORD)) {
                action.addFieldId(paramName, fieldId);
                return;
            }
            if (Objects.equals(key.trim(), TRANSITION_KEYWORD)) {
                action.addTransitionId(paramName, transitionId);
                return;
            }
        }
        if (Objects.equals(key.trim(), FIELD_KEYWORD)) {
            action.addFieldId(paramName, getFieldId(importId));
            return;
        }
        if (Objects.equals(key.trim(), TRANSITION_KEYWORD)) {
            action.addTransitionId(paramName, importId);
            return;
        }
        throw new IllegalArgumentException("Object " + key + "." + importId + " not supported");
    }

    private Map<String, String> parseParams(String definition) {
        List<String> params = Arrays.asList(definition.split(","));
        return params.stream()
                .map(param -> param.split(":"))
                .collect(Collectors.toMap(o -> o[0], o -> o[1]));
    }

    private String getFieldId(String importId) {
        try {
            return getField(importId).getStringId();
        } catch (Exception e) {
            throw new IllegalArgumentException("Object f." + importId + " does not exists");
        }
    }

    @Transactional
    protected void addTrigger(Transition transition, com.netgrif.workflow.importer.model.Trigger importTrigger) {
        Trigger trigger = triggerFactory.buildTrigger(importTrigger);

        transition.addTrigger(trigger);
    }

    @Transactional
    protected void createPlace(com.netgrif.workflow.importer.model.Place importPlace) {
        Place place = new Place();
        place.setImportId(importPlace.getId());
        if (importPlace.isStatic() == null)
            place.setIsStatic(importPlace.isIsStatic());
        else
            place.setIsStatic(importPlace.isStatic());
        place.setTokens(importPlace.getTokens());
        place.setPosition(importPlace.getX(), importPlace.getY());
        place.setTitle(toI18NString(importPlace.getLabel()));

        net.addPlace(place);
        places.put(importPlace.getId(), place);
    }

    @Transactional
    protected void createRole(Role importRole) {
        ProcessRole role = new ProcessRole();
        role.setImportId(importRole.getId());
        if (importRole.getName() == null)
            role.setName(toI18NString(importRole.getTitle()));
        else
            role.setName(toI18NString(importRole.getName()));
        if (config.isNotSaveObjects())
            role = roleRepository.save(role);
        else
            role.set_id(new ObjectId());

        net.addRole(role);
        roles.put(importRole.getId(), role);
    }

    @Transactional
    protected void createTransaction(com.netgrif.workflow.importer.model.Transaction importTransaction) {
        Transaction transaction = new Transaction();
        transaction.setTitle(toI18NString(importTransaction.getTitle()));
        transaction.setImportId(importTransaction.getId());

        net.addTransaction(transaction);
        transactions.put(importTransaction.getId(), transaction);
    }

    @Transactional
    protected Node getNode(String id) {
        if (places.containsKey(id))
            return getPlace(id);
        else if (transitions.containsKey(id))
            return getTransition(id);
        throw new IllegalArgumentException("Node with id [" + id + "] not found.");
    }

    I18nString toI18NString(I18NStringType imported) {
        if (imported == null)
            return null;
        I18nString string = i18n.getOrDefault(imported.getName(), new I18nString(imported.getName(), imported.getValue()));
        if (string.getDefaultValue() == null)
            string.setDefaultValue(imported.getValue());
        return string;
    }

    private boolean isDefaultRoleAllowedFor(com.netgrif.workflow.importer.model.Transition transition, Document document) {
        // FALSE if defaultRole not allowed in net
        if (document.isDefaultRole() != null && !document.isDefaultRole())
            return false;
        // FALSE if role or trigger mapping
        for (Mapping mapping: document.getMapping()) {
            if (mapping.getTransitionRef() == transition.getId() && (mapping.getRoleRef() != null && !mapping.getRoleRef().isEmpty()) && (mapping.getTrigger() != null && !mapping.getTrigger().isEmpty()))
                return false;
        }
        // TRUE if no roles and no triggers
        return (transition.getRoleRef() == null || transition.getRoleRef().isEmpty()) && (transition.getTrigger() == null || transition.getTrigger().isEmpty());
    }

    PetriNet getNetByImportId(Long id) {
        Optional<PetriNet> net = service.findByImportId(id);
        if (!net.isPresent())
            throw new IllegalArgumentException();
        return net.get();
    }

    private AssignPolicy toAssignPolicy(AssignPolicyType type) {
        if (type == null || type.value() == null)
            return AssignPolicy.MANUAL;

        return AssignPolicy.valueOf(type.value().toUpperCase());
    }

    private DataFocusPolicy toDataFocusPolicy(DataFocusPolicyType type) {
        if (type == null || type.value() == null)
            return DataFocusPolicy.MANUAL;

        return DataFocusPolicy.valueOf(type.value().toUpperCase());
    }

    private FinishPolicy toFinishPolicy(FinishPolicyType type) {
        if (type == null || type.value() == null)
            return FinishPolicy.MANUAL;

        return FinishPolicy.valueOf(type.value().toUpperCase());
    }

    public ProcessRole getRole(String id) {
        ProcessRole role = roles.get(id);
        if (role == null)
            throw new IllegalArgumentException("Role " + id + " not found");
        return role;
    }

    public Field getField(String id) {
        Field field = fields.get(id);
        if (field == null)
            throw new IllegalArgumentException("Field " + id + " not found");
        return field;
    }

    public Transition getTransition(String id) {
        Transition transition = transitions.get(id);
        if (transition == null)
            throw new IllegalArgumentException("Transition " + id + " not found");
        return transition;
    }

    public Place getPlace(String id) {
        Place place = places.get(id);
        if (place == null)
            throw new IllegalArgumentException("Place " + id + " not found");
        return place;
    }

    public Transaction getTransaction(String id) {
        Transaction transaction = transactions.get(id);
        if (transaction == null)
            throw new IllegalArgumentException("Transaction " + id + " not found");
        return transaction;
    }

    public I18nString getI18n(String id) {
        return i18n.get(id);
    }
}