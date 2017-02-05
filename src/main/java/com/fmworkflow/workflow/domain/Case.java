package com.fmworkflow.workflow.domain;

import com.fmworkflow.petrinet.domain.PetriNet;
import com.fmworkflow.petrinet.domain.Place;
import com.fmworkflow.petrinet.domain.Transition;
import com.fmworkflow.petrinet.domain.throwable.TransitionNotStartableException;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.HashMap;
import java.util.Map;

@Document
public class Case {
    @DBRef
    private PetriNet petriNet;
    @Field("activePlaces")
    private Map<String, Integer> activePlaces;
    private String title;
    @Field("dataset")
    private DataSet dataSet;

    public Case() {
        activePlaces = new HashMap<>();
    }

    public Case(String title) {
        this();
        this.title = title;
    }

    public Case(String title, PetriNet petriNet, Map<String, Integer> activePlaces) {
        this(title);
        this.petriNet = petriNet;
        this.activePlaces = activePlaces;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public void setDataSet(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    public PetriNet getPetriNet() {
        return petriNet;
    }

    public void setPetriNet(PetriNet petriNet) {
        this.petriNet = petriNet;
    }

    public Map<String, Integer> getActivePlaces() {
        return activePlaces;
    }

    public void setActivePlaces(Map<String, Integer> activePlaces) {
        this.activePlaces = activePlaces;
    }

    public void addActivePlace(String placeId, Integer tokens) {
        this.activePlaces.put(placeId, tokens);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void finishTransition(Transition transition) {
        Map<Place, Integer> outputPlaces = petriNet.getOutputPlaces(transition);
        for (Map.Entry<Place, Integer> entry : outputPlaces.entrySet()) {
            addTokensToPlace(entry.getKey(), entry.getValue());
        }
    }

    private void addTokensToPlace(Place place, Integer tokens) {
        Integer newTokens = tokens;
        String id = place.getStringId();
        if (activePlaces.containsKey(id))
            newTokens += activePlaces.get(id);
        activePlaces.put(id, newTokens);
    }

    // TODO: 5. 2. 2017 make transactional
    public void startTransition(Transition transition) throws TransitionNotStartableException {
        Map<Place, Integer> inputPlaces = petriNet.getInputPlaces(transition);
        for (Map.Entry<Place, Integer> entry : inputPlaces.entrySet()) {
            if (isNotActivePlace(entry.getKey()))
                throw new TransitionNotStartableException();
            removeTokensFromActivePlace(entry.getKey(), entry.getValue());
        }
    }

    private void removeTokensFromActivePlace(Place place, Integer tokens) {
        String id = place.getStringId();
        activePlaces.put(id, activePlaces.get(id) - tokens);
    }

    private boolean isNotActivePlace(Place place) {
        return !isActivePlace(place);
    }
    private boolean isActivePlace(Place place) {
        return activePlaces.containsKey(place.getStringId());
    }
}