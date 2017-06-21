package com.netgrif.workflow.importer;

import com.netgrif.workflow.importer.model.*;
import com.netgrif.workflow.importer.model.DataLogic;
import com.netgrif.workflow.petrinet.domain.*;
import com.netgrif.workflow.petrinet.domain.dataset.Field;
import com.netgrif.workflow.petrinet.domain.dataset.logic.DataBehavior;
import com.netgrif.workflow.petrinet.domain.repositories.PetriNetRepository;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRole;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRoleRepository;
import com.netgrif.workflow.petrinet.service.ArcFactory;
import com.netgrif.workflow.workflow.domain.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.*;

@Component
public class Importer {

    public static final String FIELD_KEYWORD = "f";
    public static final String TRANSITION_KEYWORD = "t";

    private Document document;
    private PetriNet net;
    private Map<Long, ProcessRole> roles;
    private Map<Long, Field> fields;
    private Map<Long, Transition> transitions;
    private Map<Long, Place> places;

    private ImportFieldFactory fieldFactory;
    private ImportTriggerFactory triggerFactory;

    @Autowired
    private PetriNetRepository repository;

    @Autowired
    private ProcessRoleRepository roleRepository;

    public Importer() {
        this.roles = new HashMap<>();
        this.transitions = new HashMap<>();
        this.places = new HashMap<>();
        this.fields = new HashMap<>();
        this.fieldFactory = new ImportFieldFactory(this);
        this.triggerFactory = new ImportTriggerFactory(this);
    }

    @Transactional
    public PetriNet importPetriNet(File xml, String title, String initials) {
        try {
            unmarshallXml(xml);
            return createPetriNet(title, initials);
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Transactional
    private void unmarshallXml(File xml) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(Document.class);

        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        document = (Document) jaxbUnmarshaller.unmarshal(xml);
    }

    @Transactional
    private PetriNet createPetriNet(String title, String initials) {
        net = new PetriNet();
        net.setTitle(title);
        net.setInitials(initials);

        // TODO: 15. 4. 2017 check if document contains any roles, data, etc. (NullPointerException)
        Arrays.stream(document.getImportRoles()).forEach(this::createRole);
        Arrays.stream(document.getImportData()).forEach(this::createDataSet);
        Arrays.stream(document.getImportPlaces()).forEach(this::createPlace);
        Arrays.stream(document.getImportTransitions()).forEach(this::createTransition);
        Arrays.stream(document.getImportArc()).forEach(this::createArc);

        return repository.save(net);
    }

    @Transactional
    private void createArc(ImportArc importArc) {
        Arc arc = ArcFactory.getArc(importArc.getType());
        arc.setMultiplicity(importArc.getMultiplicity());
        arc.setSource(getNode(importArc.getSourceId()));
        arc.setDestination(getNode(importArc.getDestinationId()));

        net.addArc(arc);
    }

    @Transactional
    private void createDataSet(ImportData importData) {
        Field field = fieldFactory.getField(importData);

        net.addDataSetField(field);
        fields.put(importData.getId(), field);
    }

    @Transactional
    private void createTransition(ImportTransition importTransition) {
        Transition transition = new Transition();
        transition.setTitle(importTransition.getLabel());
        transition.setPosition(importTransition.getX(), importTransition.getY());

        if (importTransition.getRoleRef() != null) {
            Arrays.stream(importTransition.getRoleRef()).forEach(roleRef ->
                    addRoleLogic(transition, roleRef)
            );
        }
        if (importTransition.getDataRef() != null) {
            Arrays.stream(importTransition.getDataRef()).forEach(dataRef ->
                    addDataLogic(transition, dataRef)
            );
        }
//        if (importTransition.getTrigger() != null) {
//            Arrays.stream(importTransition.getTrigger()).forEach(trigger ->
//                    addTrigger(transition, trigger)
//            );
//        }

        net.addTransition(transition);
        transitions.put(importTransition.getId(), transition);
    }

    @Transactional
    private void addRoleLogic(Transition transition, RoleRef roleRef) {
        RoleLogic logic = roleRef.getLogic();
        String roleId = roles.get(roleRef.getId()).getObjectId();

        if (logic == null || roleId == null)
            return;

        transition.addRole(roleId,ImportRoleFactory.getPermissions(logic));
//        if (logic.getPerform())
//            transition.addRole(roleId, new AssignFunction(roleId));
//        if (logic.getDelegate())
//            transition.addRole(roleId, new DelegateFunction(roleId));
    }

    @Transactional
    private void addDataLogic(Transition transition, DataRef dataRef) {
        DataLogic logic = dataRef.getLogic();
        String fieldId = fields.get(dataRef.getId()).getObjectId();

        if (logic == null || fieldId == null)
            return;

        Set<DataBehavior> behavior = new HashSet<>();
        if(logic.getBehavior() != null)
            Arrays.stream(logic.getBehavior()).forEach(b -> behavior.add(DataBehavior.fromString(b)));

        final Set<String> actions = new HashSet<>();
        if(logic.getAction() != null) {
            Arrays.asList(logic.getAction()).forEach(action -> {
                action = parseObjectIds(action, fieldId, FIELD_KEYWORD);
                action = parseObjectIds(action, transition.getStringId(),TRANSITION_KEYWORD);
                actions.add(action);
            });
        }

        transition.addDataSet(fieldId,behavior,actions);
    }

    @Transactional
    private String parseObjectIds(String action, String currentId, String processedObject){
        action = action.replace("\n","").replace("  ","");
        int last = 0;
        while(true){
            int start = action.indexOf(processedObject+".",last);
            if(start == -1) break;
            int coma = action.indexOf(',',start);
            int semicolon = action.indexOf(';',start);
            int delimeter = coma < semicolon && coma != -1 ? coma : semicolon;

            String id = action.substring(start+2,delimeter);
            String objectId = id.equalsIgnoreCase("this") ? currentId : getObjectId(processedObject,Long.parseLong(id));

            action = action.replace(processedObject+"."+id, processedObject+"."+objectId);

            if(delimeter == semicolon) break;
            else last = coma + (objectId.length() - id.length());
        }
        return action;
    }

    private String getObjectId(String processedObject, Long xmlId){
        if(processedObject.equalsIgnoreCase(FIELD_KEYWORD)) return fields.get(xmlId).getObjectId();
        if(processedObject.equalsIgnoreCase(TRANSITION_KEYWORD)) return transitions.get(xmlId).getStringId();
        return "";
    }

    @Transactional
    private void addTrigger(Transition transition, ImportTrigger importTrigger) {
        Trigger trigger = triggerFactory.buildTrigger(importTrigger);

        transition.addTrigger(trigger);
    }

    @Transactional
    private void createPlace(ImportPlace importPlace) {
        Place place = new Place();
        place.setStatic(importPlace.getIsStatic());
        place.setTokens(importPlace.getTokens());
        place.setPosition(importPlace.getX(), importPlace.getY());
        place.setTitle(importPlace.getLabel());

        net.addPlace(place);
        places.put(importPlace.getId(), place);
    }

    @Transactional
    private void createRole(ImportRole importRole) {
        ProcessRole role = new ProcessRole();
        role.setName(importRole.getName());
        role = roleRepository.save(role);

        net.addRole(role);
        roles.put(importRole.getId(), role);
    }

    @Transactional
    private Node getNode(Long id) {
        // TODO: 18/02/2017 maybe throw exception if transitions doesn't contain id
        if (places.containsKey(id))
            return places.get(id);
        else
            return transitions.get(id);
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public PetriNet getNet() {
        return net;
    }

    public void setNet(PetriNet net) {
        this.net = net;
    }

    public Map<Long, ProcessRole> getRoles() {
        return roles;
    }

    public void setRoles(Map<Long, ProcessRole> roles) {
        this.roles = roles;
    }

    public Map<Long, Field> getFields() {
        return fields;
    }

    public void setFields(Map<Long, Field> fields) {
        this.fields = fields;
    }

    public Map<Long, Transition> getTransitions() {
        return transitions;
    }

    public void setTransitions(Map<Long, Transition> transitions) {
        this.transitions = transitions;
    }

    public Map<Long, Place> getPlaces() {
        return places;
    }

    public void setPlaces(Map<Long, Place> places) {
        this.places = places;
    }

    public PetriNetRepository getRepository() {
        return repository;
    }

    public void setRepository(PetriNetRepository repository) {
        this.repository = repository;
    }

    public ProcessRoleRepository getRoleRepository() {
        return roleRepository;
    }

    public void setRoleRepository(ProcessRoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }
}