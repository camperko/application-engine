package com.netgrif.workflow.workflow.service;

import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.dataset.Field;
import com.netgrif.workflow.petrinet.domain.dataset.FieldWithDefault;
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.DataField;
import com.netgrif.workflow.workflow.domain.repositories.CaseRepository;
import com.netgrif.workflow.workflow.service.interfaces.ITaskService;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService;
import com.netgrif.workflow.workflow.web.WorkflowController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.LongStream;

@Service
public class WorkflowService implements IWorkflowService {

    @Autowired
    private CaseRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private IPetriNetService petriNetService;

    @Autowired
    private ITaskService taskService;

    @Override
    public Case saveCase(Case useCase) {
        return repository.save(useCase);
    }

    @Override
    public Page<Case> getAll(Pageable pageable) {
        //page.getContent().forEach(aCase -> aCase.getPetriNet().initializeArcs());
        return setImmediateDataFields(repository.findAll(pageable));
    }

    public Page<Case> searchCase(List<String> nets, Pageable pageable) {
        StringBuilder queryBuilder = new StringBuilder();
        nets.forEach(net -> {
            queryBuilder.append("{$ref:\"petriNet\",$id:{$oid:\"");
            queryBuilder.append(net);
            queryBuilder.append("\"}},");
        });
        if (queryBuilder.length() > 0)
            queryBuilder.deleteCharAt(queryBuilder.length() - 1);
        String queryString = nets.isEmpty() ? "{}" : "{petriNet:{$in:[" + queryBuilder.toString() + "]}}";
        BasicQuery query = new BasicQuery(queryString);
        query = (BasicQuery) query.with(pageable);
        List<Case> useCases = mongoTemplate.find(query, Case.class);
        return setImmediateDataFields(new PageImpl<Case>(useCases, pageable, mongoTemplate.count(new BasicQuery(queryString, "{_id:1}"), Case.class)));
    }

    @Override
    public Case createCase(String netId, String title, String color, Long authorId) {
        PetriNet petriNet = petriNetService.loadPetriNet(netId);
        Case useCase = new Case(title, petriNet, petriNet.getActivePlaces());
        useCase.setColor(color);
        useCase.setAuthor(authorId);
        useCase = saveCase(useCase);
        taskService.createTasks(useCase);
        return setImmediateDataFields(useCase);
    }

    @Override
    public Page<Case> findAllByAuthor(Long authorId, String petriNet, Pageable pageable) {
        String queryString = "{author:"+authorId+", petriNet:{$ref:\"petriNet\",$id:{$oid:\""+petriNet+"\"}}}";
        BasicQuery query = new BasicQuery(queryString);
        query = (BasicQuery)query.with(pageable);
        List<Case> cases = mongoTemplate.find(query,Case.class);
        return setImmediateDataFields(new PageImpl<Case>(cases,pageable,mongoTemplate.count(new BasicQuery(queryString,"{_id:1}"),Case.class)));
    }

    @Override
    public void deleteCase(String caseId){
        repository.delete(caseId);
        taskService.deleteTasksByCase(caseId);
    }

    public List<Field> getData(String caseId){
        Case useCase = repository.findOne(caseId);
        List<Field> fields = new ArrayList<>();
        useCase.getDataSet().forEach((id, dataField) -> {
            if(dataField.isDisplayable() || useCase.getPetriNet().isDisplayableInAnyTransition(id)){
                Field field = TaskService.buildField(useCase,id,false);
                field.setBehavior(dataField.applyOnlyVisibleBehavior());
                fields.add(field);
            }
        });

        LongStream.range(0L,fields.size()).forEach(l -> fields.get((int)l).setOrder(l));
        return fields;
    }

    private Page<Case> setImmediateDataFields(Page<Case> cases){
        cases.getContent().forEach(this::setImmediateDataFields);
        return cases;
    }

    private Case setImmediateDataFields(Case useCase){
        useCase.populateImmediateData();
        return useCase;
    }
}