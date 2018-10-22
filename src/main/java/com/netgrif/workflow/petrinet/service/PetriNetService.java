package com.netgrif.workflow.petrinet.service;

import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.service.UserProcessRoleService;
import com.netgrif.workflow.event.events.model.UserImportModelEvent;
import com.netgrif.workflow.importer.service.Config;
import com.netgrif.workflow.importer.service.Importer;
import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.Transition;
import com.netgrif.workflow.petrinet.domain.arcs.VariableArc;
import com.netgrif.workflow.petrinet.domain.dataset.Field;
import com.netgrif.workflow.petrinet.domain.repositories.PetriNetRepository;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRole;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRoleRepository;
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService;
import com.netgrif.workflow.petrinet.web.requestbodies.UploadedFileMeta;
import com.netgrif.workflow.petrinet.web.responsebodies.DataFieldReference;
import com.netgrif.workflow.petrinet.web.responsebodies.PetriNetReference;
import com.netgrif.workflow.petrinet.web.responsebodies.TransitionReference;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService.transformToReference;

@Service
public abstract class PetriNetService implements IPetriNetService {

    private static final Logger log = Logger.getLogger(PetriNetService.class);

    @Lookup("importer")
    abstract Importer getImporter();

    @Autowired
    private UserProcessRoleService userProcessRoleService;

    @Autowired
    private ProcessRoleRepository processRoleRepository;

    @Autowired
    private PetriNetRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ApplicationEventPublisher publisher;

    private Map<ObjectId, PetriNet> cache = new HashMap<>();

    @Override
    public PetriNet clone(ObjectId petriNetId) {
        PetriNet net = cache.get(petriNetId);
        if (net == null) {
            net = repository.findOne(petriNetId.toString());
            if (net == null) {
                throw new IllegalArgumentException("Petri net with id [" + petriNetId + "] not found");
            }
            cache.put(petriNetId, net);
        }
        return net.clone();
    }

    @Override
    public Optional<PetriNet> importPetriNetAndDeleteFile(File xmlFile, UploadedFileMeta netMetaData, LoggedUser user) throws IOException {
        Optional<PetriNet> imported = importPetriNet(xmlFile, netMetaData, user);
        if (!xmlFile.delete())
            throw new IOException("File of process was not deleted");
        return imported;
    }

    @Override
    public Optional<PetriNet> importPetriNet(File xmlFile, UploadedFileMeta metaData, LoggedUser user) throws IOException {
        PetriNet existingNet = getNewestVersionByIdentifier(metaData.identifier);
        Optional<PetriNet> newPetriNet;
        if (existingNet == null) {
            newPetriNet = importNewPetriNet(xmlFile, metaData, user);
        } else {
            //TODO 3.4.2018 compare net hash with found net hash -> if equal do not save network => possible duplicate
            newPetriNet = importNewVersion(xmlFile, metaData, existingNet, user);
        }

        if (newPetriNet.isPresent()) {
            PetriNet net = newPetriNet.get();
            cache.put(net.getObjectId(), net);
        }

        return newPetriNet;
    }

    private Optional<PetriNet> importNewPetriNet(File xmlFile, UploadedFileMeta metaData, LoggedUser user) {
        Optional<PetriNet> imported = getImporter().importPetriNet(xmlFile, metaData.name, metaData.initials, new Config());
        imported.ifPresent(petriNet -> {
            userProcessRoleService.saveRoles(imported.get().getRoles().values(), imported.get().getStringId());

            try {
                setupImportedPetriNet(imported.get(), xmlFile, metaData, user);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        return imported;
    }

    private Optional<PetriNet> importNewVersion(File xmlFile, UploadedFileMeta meta, @NotNull PetriNet previousVersion, LoggedUser user) {
        Config config = Config.builder()
                .notSaveObjects(true)
                .build();

        Optional<PetriNet> imported = getImporter().importPetriNet(xmlFile, meta.name, meta.initials, config);
        imported.ifPresent(petriNet -> {
            petriNet.setVersion(previousVersion.getVersion());
            petriNet.incrementVersion(PetriNet.VersionType.valueOf(meta.releaseType.trim().toUpperCase()));
            List<ProcessRole> newRoles = migrateProcessRoles(petriNet, previousVersion);

            try {
                setupImportedPetriNet(petriNet, xmlFile, meta, user);
                userProcessRoleService.saveRoles(newRoles, petriNet.getStringId());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        return imported;
    }

    private List<ProcessRole> migrateProcessRoles(PetriNet newVersion, PetriNet previousVersion) {
        Map<String, String> twins = new HashMap<>(); //key is new roles, value is old role => key is which role should be replaced and value by what
        List<ProcessRole> newRoles = new ArrayList<>();
        Map<String, ProcessRole> oldRolesByName = new HashMap<>();
        previousVersion.getRoles().forEach((id, role) -> oldRolesByName.put(role.getName().getDefaultValue(), role));

        newVersion.getRoles().forEach((id, role) -> {
            if (oldRolesByName.containsKey(role.getName().getDefaultValue())) {
                twins.put(role.getStringId(), oldRolesByName.get(role.getName().getDefaultValue()).getStringId());
            } else {
                newRoles.add(role);
            }
        });

        //replace new role with old
        twins.forEach((newId, oldId) -> {
            //replace in Petri Net
            newVersion.getRoles().remove(newId);
            newVersion.addRole(previousVersion.getRoles().get(oldId));

            //replace in transitions
            newVersion.getTransitions().forEach((transId, transition) -> {
                if (transition.getRoles().containsKey(newId)) {
                    transition.addRole(oldId, transition.getRoles().remove(newId));
                }
            });
        });

        return processRoleRepository.save(newRoles);
    }

    private void setupImportedPetriNet(PetriNet net, File xmlFile, UploadedFileMeta meta, LoggedUser user) throws IOException {
        net.setAuthor(user.transformToAuthor());
        if (meta.identifier != null && !meta.identifier.isEmpty()) {
            net.setIdentifier(meta.identifier);
        }

        net = repository.save(net);
        log.info("Petri net " + meta.name + " (" + meta.initials + " v" + net.getVersion() + ") imported successfully");
        publisher.publishEvent(new UserImportModelEvent(user, xmlFile, meta.name, meta.initials));

        getImporter().saveNetFile(net, xmlFile);
    }


    @Override
    public Optional<PetriNet> saveNew(PetriNet petriNet) {
        initializeVariableArcs(petriNet);

        return Optional.of(repository.save(petriNet));
    }

    @Override
    public PetriNet getPetriNet(String id) {
        PetriNet net = repository.findOne(id);
        if (net == null)
            throw new IllegalArgumentException("No Petri net with id: " + id + " was found.");

        net.initializeArcs();
        return net;
    }

    @Override
    public PetriNet getPetriNet(String identifier, String version) {
        PetriNet net = repository.findByIdentifierAndVersion(identifier, version);
        if (net == null)
            return null;
        net.initializeArcs();
        return net;
    }

    @Override
    public List<PetriNet> getByIdentifier(String identifier) {
        List<PetriNet> nets = repository.findAllByIdentifier(identifier);
        nets.forEach(PetriNet::initializeArcs);
        return nets;
    }

    @Override
    public PetriNet getNewestVersionByIdentifier(String identifier) {
        List<PetriNet> nets = repository.findByIdentifier(identifier, new PageRequest(0, 1, Sort.Direction.DESC, "version")).getContent();
        if (nets.isEmpty())
            return null;
        return nets.get(0);
    }

    @Override
    public List<PetriNet> getAll() {
        List<PetriNet> nets = repository.findAll();
        nets.forEach(PetriNet::initializeArcs);
        return nets;
    }

    @Override
    public FileSystemResource getFile(String netId, String title) {
        if (title == null || title.length() == 0) {
            Query query = Query.query(Criteria.where("_id").is(new ObjectId(netId)));
            query.fields().include("_id").include("title");
            List<PetriNet> nets = mongoTemplate.find(query, PetriNet.class);
            if (nets.isEmpty())
                return null;
            title = nets.get(0).getTitle().getDefaultValue();
        }
        return new FileSystemResource(Importer.ARCHIVED_FILES_PATH + netId + "-" + title + Importer.FILE_EXTENSION);
    }


//    public static PetriNetReference transformToReference(PetriNet net, Locale locale) {
//        return new PetriNetReference(net.getStringId(), net.getIdentifier(), net.getVersion(), net.getTitle().getTranslation(locale), net.getInitials());
//    }
//
//    public static TransitionReference transformToReference(PetriNet net, Transition transition, Locale locale) {
//        return new TransitionReference(transition.getStringId(), transition.getTitle().getTranslation(locale), net.getStringId());
//    }
//
//    public static DataFieldReference transformToReference(PetriNet net, Transition transition, Field field, Locale locale) {
//        return new DataFieldReference(field.getStringId(), field.getName().getTranslation(locale), net.getStringId(), transition.getStringId());
//    }

    @Override
    public List<PetriNetReference> getReferences(LoggedUser user, Locale locale) {
        return getAll().stream().map(net -> transformToReference(net, locale)).collect(Collectors.toList());
    }

    @Override
    public List<PetriNetReference> getReferencesByIdentifier(String identifier, LoggedUser user, Locale locale) {
        return getByIdentifier(identifier).stream().map(net -> transformToReference(net, locale)).collect(Collectors.toList());
    }

    @Override
    public List<PetriNetReference> getReferencesByVersion(String version, LoggedUser user, Locale locale) {
        List<PetriNetReference> references;

        if (version.contains("^")) {
            GroupOperation groupByIdentifier = Aggregation.group("identifier").max("version").as("version");
            Aggregation aggregation = Aggregation.newAggregation(groupByIdentifier);
            AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, "petriNet", Document.class);
            references = results.getMappedResults().parallelStream()
                    .map(doc -> getReference(doc.getString("_id"), doc.getString("version"), user, locale))
                    .collect(Collectors.toList());
        } else {
            references = repository.findAllByVersion(version).stream()
                    .map(net -> transformToReference(net, locale)).collect(Collectors.toList());
        }

        return references;
    }

    @Override
    public List<PetriNetReference> getReferencesByUsersProcessRoles(LoggedUser user, Locale locale) {
        Query query = Query.query(getProcessRolesCriteria(user));
        return mongoTemplate.find(query, PetriNet.class).stream().map(net -> transformToReference(net, locale)).collect(Collectors.toList());
    }

    @Override
    public PetriNetReference getReference(String identifier, String version, LoggedUser user, Locale locale) {
        PetriNet net = version.contains("^") ? getNewestVersionByIdentifier(identifier) : getPetriNet(identifier, version);
        return net != null ? transformToReference(net, locale) : new PetriNetReference();
    }

    @Override
    public List<TransitionReference> getTransitionReferences(List<String> netIds, LoggedUser user, Locale locale) {
        Iterable<PetriNet> nets = repository.findAll(netIds);
        List<TransitionReference> references = new ArrayList<>();

        nets.forEach(net -> references.addAll(net.getTransitions().entrySet().stream()
                .map(entry -> transformToReference(net, entry.getValue(), locale)).collect(Collectors.toList())));

        return references;
    }

    @Override
    public List<DataFieldReference> getDataFieldReferences(List<TransitionReference> transitions, Locale locale) {
        Iterable<PetriNet> nets = repository.findAll(transitions.stream().map(TransitionReference::getPetriNetId).collect(Collectors.toList()));
        List<DataFieldReference> dataRefs = new ArrayList<>();
        Map<String, List<TransitionReference>> transitionReferenceMap = transitions.stream()
                .collect(Collectors.groupingBy(TransitionReference::getPetriNetId));

        nets.forEach(net -> transitionReferenceMap.get(net.getStringId())
                .forEach(transition -> {
                    Transition trans;
                    if ((trans = net.getTransition(transition.getStringId())) != null) {
                        dataRefs.addAll(trans.getDataSet().entrySet().stream()
                                .map(entry -> transformToReference(net, trans, net.getDataSet().get(entry.getKey()), locale))
                                .collect(Collectors.toList()));
                    }
                }));

        return dataRefs;
    }

    @Override
    public Optional<PetriNet> findByImportId(long id) {
        return Optional.of(repository.findByImportId(id));
    }

    public Page<PetriNetReference> search(Map<String, Object> criteria, LoggedUser user, Pageable pageable, Locale locale) {
        Query query = new Query();

        if (!user.isAdmin())
            query.addCriteria(getProcessRolesCriteria(user));

        criteria.forEach((key, value) -> {
            Criteria valueCriteria;
            if (value instanceof List)
                valueCriteria = Criteria.where(key).in(value);
            else if (key.equalsIgnoreCase("title") || key.equalsIgnoreCase("initials") || key.equalsIgnoreCase("identifier"))
                valueCriteria = Criteria.where(key).regex((String) value, "i");
            else
                valueCriteria = Criteria.where(key).is(value);
            query.addCriteria(valueCriteria);
        });

        query.with(pageable);
        List<PetriNet> nets = mongoTemplate.find(query, PetriNet.class);
        return PageableExecutionUtils.getPage(nets.stream()
                        .map(net -> new PetriNetReference(net, locale)).collect(Collectors.toList()),
                pageable,
                () -> mongoTemplate.count(query, PetriNet.class));
    }

    private Criteria getProcessRolesCriteria(LoggedUser user) {
        return new Criteria().orOperator(user.getProcessRoles().stream()
                .map(role -> Criteria.where("roles." + role).exists(true)).toArray(Criteria[]::new));
    }

    private void initializeVariableArcs(PetriNet net) {
        net.getArcs().values().stream()
                .flatMap(List::stream)
                .filter(arc -> arc instanceof VariableArc)
                .forEach(arc -> initializeVariableArc(net, (VariableArc) arc));
    }

    private void initializeVariableArc(PetriNet net, VariableArc arc) {
        Optional<Field> field = net.getField(arc.getMultiplicity().toString());
        if (!field.isPresent())
            throw new IllegalArgumentException("Field with import id " + arc.getMultiplicity() + " not found.");
        arc.setFieldId(field.get().getStringId());
    }
}