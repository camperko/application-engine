package com.fmworkflow.workflow.domain.repositories;

import com.fmworkflow.workflow.domain.Case;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CaseRepository extends MongoRepository<Case, String>{
}