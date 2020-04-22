package com.netgrif.workflow.startup


import com.netgrif.workflow.petrinet.service.PetriNetService
import com.netgrif.workflow.workflow.domain.repositories.CaseRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@ConditionalOnProperty(value = "admin.create-super", matchIfMissing = true)
@Component
class FinisherRunner extends AbstractOrderedCommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FinisherRunner)

    @Autowired
    private SuperCreator superCreator

    @Autowired
    private PetriNetService petriNetService

    @Autowired
    private ImportHelper helper

    @Autowired
    private CaseRepository caseRepository

    @Override
    void run(String... strings) throws Exception {
//        def address = helper.createNet("mortgage/address.xml", "address", "Address", "ADD", "major")
//        def financial = helper.createNet("mortgage/financial_data.xml", "financial_data", "Financial data", "FIN", "major")
//        def personal = helper.createNet("mortgage/personal_information.xml", "personal_information", "Personal information", "PER", "major")
//        def mortgage = helper.createNet("mortgage/mortgage.xml", "mortgage", "Mortgage", "MOR", "major")
//
//        superCreator.setAllToSuperUser()
//
//        def net = helper.createNet("all_data.xml", "all_data.xml", "all_data.xml", "all_data.xml", "major")
//        assert net.isPresent()
//
//        15.times {
//            helper.createCase("Case $it" as String, net.get())
//        }
    }
}