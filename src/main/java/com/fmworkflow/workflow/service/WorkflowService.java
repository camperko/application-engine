package com.fmworkflow.workflow.service;

import com.fmworkflow.petrinet.domain.PetriNet;
import com.fmworkflow.petrinet.domain.Place;
import com.fmworkflow.petrinet.service.IPetriNetService;
import com.fmworkflow.workflow.domain.Case;
import com.fmworkflow.workflow.domain.CaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowService implements IWorkflowService {
    @Autowired
    private CaseRepository repository;

    @Autowired
    private IPetriNetService petriNetService;

    @Autowired
    private ITaskService taskService;

    @Override
    public void saveCase(Case useCase) {
        repository.save(useCase);
    }

    @Override
    public List<Case> getAll() {
        List<Case> cases = repository.findAll();
        cases.forEach(aCase -> aCase.getPetriNet().initializeArcs());
        return cases;
    }

    @Override
    public void createCase(String netId, String title) {
        PetriNet petriNet = petriNetService.loadPetriNet(netId);
        Map<String, Integer> activePlaces = createActivePlaces(petriNet);
        Case useCase = new Case(title, petriNet, activePlaces);
        HashMap<String, Object> dataValues = new HashMap<>();
        petriNet.getDataSet().forEach((key, field) -> dataValues.put(key,null));
        useCase.setDataSetValues(dataValues);
        saveCase(useCase);
        taskService.createTasks(useCase);
    }

    private Map<String, Integer> createActivePlaces(PetriNet petriNet) {
        Map<String, Integer> activePlaces = new HashMap<>();
        Map<String, Place> places = petriNet.getPlaces();
        for (Place place : places.values()) {
            if (place.getTokens() > 0) {
                activePlaces.put(place.getObjectId().toString(), place.getTokens());
            }
        }
        return activePlaces;
    }

//    @Override
//    public DataSet getDataForTransition(String caseId, String transitionId){
//        Case useCase = repository.findOne(caseId);
//        return useCase.getDataSet().getFieldsForTransition(transitionId);
//    }

//    @Override
//    public void modifyData(String caseId, Map<String, String> newValues){
//        Case useCase = repository.findOne(caseId);
//        for(Field field:useCase.getDataSet().getFields()){
//            if(newValues.containsKey(field.get_id().toString())){
//                field.modifyValue(newValues.get(field.get_id().toString()));
//            }
//        }
//
//        saveCase(useCase);
//    }


}