package com.netgrif.application.engine.history.domain.petrinetevents.repository;

import com.netgrif.application.engine.history.domain.petrinetevents.DeletePetriNetEventLog;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DeletePetriNetEventLogRepository extends MongoRepository<DeletePetriNetEventLog, ObjectId> {

    List<DeletePetriNetEventLog> findAllByNetId(ObjectId netId);
}
