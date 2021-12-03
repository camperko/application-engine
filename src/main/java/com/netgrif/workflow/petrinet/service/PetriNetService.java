package com.netgrif.workflow.petrinet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.service.interfaces.IUserService;
import com.netgrif.workflow.event.events.model.UserImportModelEvent;
import com.netgrif.workflow.importer.service.Importer;
import com.netgrif.workflow.importer.service.throwable.MissingIconKeyException;
import com.netgrif.workflow.orgstructure.groups.interfaces.INextGroupService;
import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.Transition;
import com.netgrif.workflow.petrinet.domain.VersionType;
import com.netgrif.workflow.petrinet.domain.dataset.logic.action.Action;
import com.netgrif.workflow.petrinet.domain.dataset.logic.action.FieldActionsRunner;
import com.netgrif.workflow.petrinet.domain.events.EventPhase;
import com.netgrif.workflow.petrinet.domain.repositories.PetriNetRepository;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRole;
import com.netgrif.workflow.petrinet.domain.throwable.MissingPetriNetMetaDataException;
import com.netgrif.workflow.petrinet.domain.version.Version;
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService;
import com.netgrif.workflow.petrinet.service.interfaces.IProcessRoleService;
import com.netgrif.workflow.petrinet.web.responsebodies.DataFieldReference;
import com.netgrif.workflow.petrinet.web.responsebodies.PetriNetReference;
import com.netgrif.workflow.petrinet.web.responsebodies.TransitionReference;
import com.netgrif.workflow.rules.domain.facts.NetImportedFact;
import com.netgrif.workflow.rules.service.interfaces.IRuleEngine;
import com.netgrif.workflow.workflow.domain.FileStorageConfiguration;
import com.netgrif.workflow.workflow.service.interfaces.IFieldActionsCacheService;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Provider;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService.transformToReference;

@Service
public class PetriNetService implements IPetriNetService {

    private static final Logger log = LoggerFactory.getLogger(PetriNetService.class);

    @Autowired
    private IProcessRoleService processRoleService;

    @Autowired
    private PetriNetRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private FileStorageConfiguration fileStorageConfiguration;

    @Autowired
    private IRuleEngine ruleEngine;

    @Autowired
    private IWorkflowService workflowService;

    @Autowired
    private INextGroupService groupService;

    @Autowired
    private Provider<Importer> importerProvider;

    @Autowired
    private FieldActionsRunner actionsRunner;

    @Autowired
    private IFieldActionsCacheService functionCacheService;

    @Autowired
    private IUserService userService;

    private Map<ObjectId, PetriNet> cache = new HashMap<>();

    protected Importer getImporter() {
        return importerProvider.get();
    }

    @Override
    public void evictCache() {
        cache = new HashMap<>();
    }

    /**
     * Get read only Petri net.
     */
    @Override
    public PetriNet get(ObjectId petriNetId) {
        PetriNet net = cache.get(petriNetId);
        if (net == null) {
            Optional<PetriNet> optional = repository.findById(petriNetId.toString());
            if (!optional.isPresent()) {
                throw new IllegalArgumentException("Petri net with id [" + petriNetId + "] not found");
            }
            net = optional.get();
            cache.put(petriNetId, net);
        }
        return net;
    }

    @Override
    public List<PetriNet> get(Collection<ObjectId> petriNetIds) {
        return petriNetIds.stream().map(this::get).collect(Collectors.toList());
    }

    @Override
    public List<PetriNet> get(List<String> petriNetIds) {
        return this.get(petriNetIds.stream().map(ObjectId::new).collect(Collectors.toList()));
    }

    @Override
    public PetriNet clone(ObjectId petriNetId) {
        return get(petriNetId).clone();
    }

    @Override
    @Deprecated
    public Optional<PetriNet> importPetriNet(InputStream xmlFile, String releaseType, LoggedUser author) throws IOException, MissingPetriNetMetaDataException, MissingIconKeyException {
        return importPetriNet(xmlFile, VersionType.valueOf(releaseType.trim().toUpperCase()), author);
    }

    @Override
    public Optional<PetriNet> importPetriNet(InputStream xmlFile, VersionType releaseType, LoggedUser author) throws IOException, MissingPetriNetMetaDataException, MissingIconKeyException {
        Optional<PetriNet> imported = getImporter().importPetriNet(copy(xmlFile));
        if (!imported.isPresent()) {
            return imported;
        }
        PetriNet net = imported.get();

        PetriNet existingNet = getNewestVersionByIdentifier(net.getIdentifier());
        if (existingNet != null) {
            net.setVersion(existingNet.getVersion());
            net.incrementVersion(releaseType);
        }
        processRoleService.saveAll(net.getRoles().values());
        net.setAuthor(author.transformToAuthor());
        functionCacheService.cachePetriNetFunctions(net);
        Path savedPath = getImporter().saveNetFile(net, xmlFile);
        log.info("Petri net " + net.getTitle() + " (" + net.getInitials() + " v" + net.getVersion() + ") imported successfully");
        publisher.publishEvent(new UserImportModelEvent(author, new File(savedPath.toString()), net.getTitle().getDefaultValue(), net.getInitials()));
        runActions(net.getPreUploadActions(), net);
        evaluateRules(net, EventPhase.PRE);
        save(net);
        runActions(net.getPostUploadActions(), net);
        evaluateRules(net, EventPhase.POST);
        save(net);
        cache.put(net.getObjectId(), net);

        return imported;
    }

    protected void evaluateRules(PetriNet net, EventPhase phase) {
        ruleEngine.evaluateRules(net, new NetImportedFact(net.getStringId(), phase));
    }

    private InputStream copy(InputStream xmlFile) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(xmlFile, baos);
        byte[] bytes = baos.toByteArray();
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public Optional<PetriNet> save(PetriNet petriNet) {
        petriNet.initializeArcs();

        return Optional.of(repository.save(petriNet));
    }

    @Override
    public PetriNet getPetriNet(String id) {
        Optional<PetriNet> net = repository.findById(id);
        if (!net.isPresent())
            throw new IllegalArgumentException("No Petri net with id: " + id + " was found.");

        net.get().initializeArcs();
        return net.get();
    }

    @Override
    public PetriNet getPetriNet(String identifier, Version version) {
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
        List<PetriNet> nets = repository.findByIdentifier(identifier, PageRequest.of(0, 1, Sort.Direction.DESC, "version.major", "version.minor", "version.patch")).getContent();
        if (nets.isEmpty())
            return null;
        return nets.get(0);
    }

    /**
     * Determines which of the provided Strings are identifiers of {@link PetriNet}s uploaded in the system.
     *
     * @param identifiers a list of Strings that represent potential PetriNet identifiers
     * @return a list containing a subset of the input strings that correspond to identifiers of PetriNets that are present in the system
     */
    @Override
    public List<String> getExistingPetriNetIdentifiersFromIdentifiersList(List<String> identifiers) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("identifier").in(identifiers)),
                Aggregation.group("identifier"),
                Aggregation.project("identifier").and("identifier").previousOperation()
        );
        AggregationResults<?> groupResults = mongoTemplate.aggregate(
                agg,
                PetriNet.class,
                TypeFactory.defaultInstance().constructType(new TypeReference<Map<String, String>>() {}).getRawClass()
        );

        List<Map<String, String>> result = (List<Map<String, String>>) groupResults.getMappedResults();
        return result.stream().flatMap(v -> v.values().stream()).collect(Collectors.toList());
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
        return new FileSystemResource(fileStorageConfiguration.getStorageArchived() + netId + "-" + title + Importer.FILE_EXTENSION);
    }

    @Override
    public List<PetriNetReference> getReferences(LoggedUser user, Locale locale) {
        return getAll().stream().map(net -> transformToReference(net, locale)).collect(Collectors.toList());
    }

    @Override
    public List<PetriNetReference> getReferencesByIdentifier(String identifier, LoggedUser user, Locale locale) {
        return getByIdentifier(identifier).stream().map(net -> transformToReference(net, locale)).collect(Collectors.toList());
    }

    @Override
    public List<PetriNetReference> getReferencesByVersion(Version version, LoggedUser user, Locale locale) {
        List<PetriNetReference> references;

        if (version == null) {
            GroupOperation groupByIdentifier = Aggregation.group("identifier").max("version").as("version");
            Aggregation aggregation = Aggregation.newAggregation(groupByIdentifier);
            AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, "petriNet", Document.class);
            references = results.getMappedResults().stream()
                    .map(doc -> {
                        Document versionDoc = doc.get("version", Document.class);
                        Version refVersion = new Version(versionDoc.getLong("major"), versionDoc.getLong("minor"), versionDoc.getLong("patch"));
                        return getReference(doc.getString("_id"), refVersion, user, locale);
                    })
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
    public PetriNetReference getReference(String identifier, Version version, LoggedUser user, Locale locale) {
        PetriNet net = version == null ? getNewestVersionByIdentifier(identifier) : getPetriNet(identifier, version);
        return net != null ? transformToReference(net, locale) : new PetriNetReference();
    }

    @Override
    public List<TransitionReference> getTransitionReferences(List<String> netIds, LoggedUser user, Locale locale) {
        Iterable<PetriNet> nets = get(netIds);
        List<TransitionReference> references = new ArrayList<>();

        nets.forEach(net -> references.addAll(net.getTransitions().entrySet().stream()
                .map(entry -> transformToReference(net, entry.getValue(), locale)).collect(Collectors.toList())));

        return references;
    }

    @Override
    public List<DataFieldReference> getDataFieldReferences(List<TransitionReference> transitions, Locale locale) {
        Iterable<PetriNet> nets = repository.findAllById(transitions.stream().map(TransitionReference::getPetriNetId).collect(Collectors.toList()));
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
    public Optional<PetriNet> findByImportId(String id) {
        return Optional.of(repository.findByImportId(id));
    }

    @Override
    public Page<PetriNetReference> search(Map<String, Object> criteria, LoggedUser user, Pageable pageable, Locale locale) {
        Query query = new Query();

        if (!user.isAdmin())
            query.addCriteria(getProcessRolesCriteria(user));

        criteria.forEach((key, value) -> {
            Criteria valueCriteria;
            if (key.equalsIgnoreCase("group")) {
                if (value instanceof List) {
                    Collection<String> authors = this.groupService.getGroupsOwnerEmails((List<String>) value);
                    valueCriteria = Criteria.where("author.email").in(authors);
                } else {
                    valueCriteria = Criteria.where("author.email").is(this.groupService.getGroupOwnerEmail((String) value));
                }
            } else if (value instanceof List)
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

    @Override
    @Transactional
    public void deletePetriNet(String processId, LoggedUser loggedUser) {
        Optional<PetriNet> petriNetOptional = repository.findById(processId);
        if (!petriNetOptional.isPresent()) {
            throw new IllegalArgumentException("Could not find process with id [" + processId + "]");
        }

        PetriNet petriNet = petriNetOptional.get();
        log.info("[" + processId + "]: Initiating deletion of Petri net " + petriNet.getIdentifier() + " version " + petriNet.getVersion().toString());

        this.userService.removeRoleOfDeletedPetriNet(petriNet);
        this.workflowService.deleteInstancesOfPetriNet(petriNet);
        this.processRoleService.deleteRolesOfNet(petriNet, loggedUser);

        log.info("[" + processId + "]: Deleting Petri net " + petriNet.getIdentifier() + " version " + petriNet.getVersion().toString());
        this.repository.deleteBy_id(petriNet.getObjectId());
        this.cache.remove(petriNet.getObjectId());
        // net functions must by removed from cache after it was deleted from repository
        this.functionCacheService.reloadCachedFunctions(petriNet);
    }

    private Criteria getProcessRolesCriteria(LoggedUser user) {
        return new Criteria().orOperator(user.getProcessRoles().stream()
                .map(role -> Criteria.where("permissions." + role).exists(true)).toArray(Criteria[]::new));
    }

    @Override
    public void runActions(List<Action> actions, PetriNet petriNet) {
        log.info("Running actions of net [" + petriNet.getStringId() + "]");

        actions.forEach(action -> {
            actionsRunner.run(action, null, petriNet.getFunctions());
        });
    }

}