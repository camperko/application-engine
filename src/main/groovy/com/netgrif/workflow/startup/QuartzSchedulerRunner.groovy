package com.netgrif.workflow.startup


import org.quartz.Scheduler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class QuartzSchedulerRunner extends AbstractOrderedCommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(QuartzSchedulerRunner)

    @Autowired
    private Scheduler scheduler

    @Override
    void run(String... strings) throws Exception {
        log.info("Starting Quartz scheduler")
        scheduler.start()
    }

}