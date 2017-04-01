package com.fmworkflow.petrinet.domain;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface PetriNetRepository extends MongoRepository<PetriNet, String> {
    List<PetriNet> findByTitle(String title);
}
