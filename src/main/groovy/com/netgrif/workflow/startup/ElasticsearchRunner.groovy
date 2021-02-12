package com.netgrif.workflow.startup

import com.netgrif.workflow.elastic.domain.ElasticCase
import com.netgrif.workflow.elastic.domain.ElasticTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate
import org.springframework.stereotype.Component

@Component
class ElasticsearchRunner extends AbstractOrderedCommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchRunner)

    @Value('${spring.data.elasticsearch.drop}')
    private boolean drop

    @Value('${spring.data.elasticsearch.cluster-name}')
    private String clusterName

    @Value('${spring.data.elasticsearch.url}')
    private String url

    @Value('${spring.data.elasticsearch.port}')
    private int port

    @Value('${spring.data.elasticsearch.index.case}')
    private String caseIndex

    @Value('${spring.data.elasticsearch.index.task}')
    private String taskIndex

    @Autowired
    private ElasticsearchTemplate template

    @Override
    void run(String... args) throws Exception {
        if (drop) {
            log.info("Dropping Elasticsearch database [${url}:${port}/${clusterName}]")
            template.deleteIndex(ElasticCase.class)
            template.deleteIndex(ElasticTask.class)
        }
        if (!template.indexExists(ElasticCase.class)) {
            log.info "Creating Elasticsearch case index"
            template.createIndex(ElasticCase.class)
        } else {
            log.info "Elasticsearch case index exists"
        }
        if (!template.indexExists(ElasticTask.class)) {
            log.info "Creating Elasticsearch task index"
            template.createIndex(ElasticTask.class)
        } else {
            log.info "Elasticsearch task index exists"
        }
        log.info("Updating Elasticsearch case mapping [${caseIndex}]")
        template.putMapping(ElasticCase.class)
        log.info("Updating Elasticsearch task mapping [${taskIndex}]")
        template.putMapping(ElasticTask.class)
    }
}