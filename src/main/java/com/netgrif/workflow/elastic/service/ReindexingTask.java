package com.netgrif.workflow.elastic.service;

import com.netgrif.workflow.elastic.domain.ElasticCase;
import com.netgrif.workflow.elastic.domain.repository.ElasticCaseRepository;
import com.netgrif.workflow.elastic.service.interfaces.IElasticCaseService;
import com.netgrif.workflow.elastic.service.interfaces.IElasticTaskService;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.QCase;
import com.netgrif.workflow.workflow.domain.repositories.CaseRepository;
import com.querydsl.core.types.dsl.BooleanExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
@ConditionalOnExpression("'${spring.data.elasticsearch.reindex}'!= 'null'")
public class ReindexingTask {

    private static final Logger log = LoggerFactory.getLogger(ReindexingTask.class);

    private int pageSize;
    private CaseRepository caseRepository;
    private ElasticCaseRepository elasticCaseRepository;
    private IElasticCaseService elasticCaseService;
    private IElasticTaskService elasticTaskService;

    private LocalDateTime lastRun;

    @Autowired
    public ReindexingTask(CaseRepository caseRepository, ElasticCaseRepository elasticCaseRepository, IElasticCaseService elasticCaseService, IElasticTaskService elasticTaskService, @Value("${spring.data.elasticsearch.reindex-size:20}") int pageSize, @Value("${spring.data.elasticsearch.reindex-from:#{null}}") Duration from) {
        this.caseRepository = caseRepository;
        this.elasticCaseRepository = elasticCaseRepository;
        this.elasticCaseService = elasticCaseService;
        this.elasticTaskService = elasticTaskService;
        this.pageSize = pageSize;

        lastRun = LocalDateTime.now();
        if (from != null) {
            lastRun = lastRun.minus(from);
        }
    }

    @Scheduled(cron = "#{springElasticsearchReindex}")
    public void reindex() {
        log.info("Reindexing stale cases: started reindexing after " + lastRun);

        BooleanExpression predicate = QCase.case$.lastModified.before(LocalDateTime.now()).and(QCase.case$.lastModified.after(lastRun.minusMinutes(2)));

        lastRun = LocalDateTime.now();
        long count = caseRepository.count(predicate);
        if (count > 0) {
            reindexAllPages(predicate, count);
        }

        log.info("Reindexing stale cases: end");
    }

    private void reindexAllPages(BooleanExpression predicate, long count) {
        long numOfPages = ((count / pageSize) + 1);
        log.info("Reindexing " + numOfPages + " pages");

        for (int page = 0; page < numOfPages; page++) {
            reindexPage(predicate, page, numOfPages);
        }
    }

    private void reindexPage(BooleanExpression predicate, int page, long numOfPages) {
        log.info("Reindexing " + (page + 1) + " / " + numOfPages);
        Page<Case> cases = caseRepository.findAll(predicate, PageRequest.of(page, pageSize));

        for (Case aCase : cases) {
            if (elasticCaseRepository.countByStringIdAndLastModified(aCase.getStringId(), Timestamp.valueOf(aCase.getLastModified()).getTime()) == 0) {
                elasticCaseService.indexNow(new ElasticCase(aCase));

            }
        }
    }
}