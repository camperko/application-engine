package com.netgrif.workflow.startup


import com.netgrif.workflow.configuration.drools.interfaces.IRefreshableKieBase
import com.netgrif.workflow.configuration.drools.interfaces.IRuleEngineGlobalsProvider
import groovy.text.SimpleTemplateEngine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class RuleEngineRunner extends AbstractOrderedCommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RuleEngineRunner)

    @Autowired
    private IRefreshableKieBase refreshableKieBase

    @Autowired
    private IRuleEngineGlobalsProvider sessionInitializer

    @Value('${drools.template.generate}')
    private boolean generate

    @Value('${drools.template.path}')
    private String templatePath

    private static final String TEMPLATE = '''
        template header
        ruleId
        salienceVal
        ruleEnabled
        dateEffective
        dateExpires
        whenCondition
        thenAction
        
        import java.util.HashMap;
        import java.util.ArrayList;
        import org.slf4j.Logger;
        import java.time.LocalDate;
        import java.time.LocalDateTime;
        import com.netgrif.workflow.utils.DateUtils;
       
        import com.netgrif.workflow.workflow.domain.*
        import com.netgrif.workflow.petrinet.domain.*
        import com.netgrif.workflow.rules.domain.facts.*
        
        import org.quartz.*
        
<%= imports %>
        
<%= globals %>
        
        template "standard_template"
        
        rule "@{ruleId}" salience @{salienceVal}
          dialect "mvel"
          enabled @{ruleEnabled}
          @{dateEffective}
          @{dateExpires}
          when
            @{whenCondition}
          then
            @{thenAction}
        end
        end template
    '''


    @Override
    void run(String... strings) throws Exception {
        log.info("Rule engine runner starting")
        if (generate) {
            log.info("Generating template to " + templatePath)
            generateTemplate()
        }

        log.info("Loading rules from database")
        refreshableKieBase.refresh()
        log.info("Rule engine runner finished")
    }

    void generateTemplate() {
        def engine = new SimpleTemplateEngine()
        def binding = [
                imports : sessionInitializer.imports().collect { "        $it" }.join(""),
                globals : sessionInitializer.globals().collect { "        ${it.toString()}" }.join(""),
        ]
        String template = engine.createTemplate(TEMPLATE).make(binding)

        File templateFile = new File(templatePath)
        templateFile.getParentFile().mkdirs()
        boolean deleted = templateFile.delete()
        if (!deleted) {
            log.warn("Previous generated template file was not deleted")
        }

        templateFile.createNewFile()
        if (!templateFile.exists()) {
            throw new IllegalStateException("Template file $templateFile.absolutePath was not created")
        }

        templateFile.write(template)

        log.info("Generated template into file ")
    }
}