package com.netgrif.workflow.migration

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface MigrationRepository extends MongoRepository<Migration, String> {

    boolean existsByTitle(String title)
}