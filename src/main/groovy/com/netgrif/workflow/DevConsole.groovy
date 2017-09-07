package com.netgrif.workflow

import com.netgrif.workflow.importer.model.Document
import com.netgrif.workflow.petrinet.domain.repositories.PetriNetRepository
import com.netgrif.workflow.workflow.domain.repositories.CaseRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE
import static org.springframework.web.bind.annotation.RequestMethod.GET

@RestController
@RequestMapping("/dev/")
@Profile("dev")
class DevConsole {

    @Autowired
    private CaseRepository caseRepository

    @Autowired
    private PetriNetRepository netRepository

    @RequestMapping(value = "/dataset/{title}", method = GET, produces = APPLICATION_JSON_VALUE)
    String dataset(@PathVariable String title) {
        def useCase = caseRepository.findAll().find { it.title == title }
        return "{ ${useCase?.dataSet?.collect { "\"${useCase?.petriNet?.dataSet?.get(it?.key)?.importId}:${useCase?.petriNet?.dataSet?.get(it?.key)?.name?.replaceAll("\n[ ]{2}", "")}\":\"${it?.value?.value as String}\"" }?.join(", ")} }"
    }

    @RequestMapping(value = "/net/{title}", method = GET, produces = APPLICATION_XML_VALUE)
    String netSnapshot(@PathVariable String title) {
        try {
            def useCase = caseRepository.findAll().find { it.title == title }
            def net = useCase.petriNet
            def xml = new File(net.importXmlPath)

            JAXBContext jaxbContext = JAXBContext.newInstance(Document.class)
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller()
            Document document = (Document) jaxbUnmarshaller.unmarshal(xml)

            document.getImportPlaces().each { importPlace ->
                importPlace.tokens = useCase.activePlaces.get(net.places.values().find {
                    it.importId == importPlace.id
                }.getStringId())
                if (importPlace.tokens == null)
                    importPlace.tokens = 0
            }
            document.getImportData().each {
                it.action = null
                it.values = null
                it.documentRefs = null
            }
            document.getImportTransitions().each {
                it.dataGroup = null
            }
            document.importRoles = null
            document.importTransactions = null
            document.importData = null

            Marshaller marshaller = jaxbContext.createMarshaller()
            StringWriter stringWriter = new StringWriter()
            marshaller.marshal(document, stringWriter)

            return stringWriter.toString()
        } catch (Exception ignored) {
            ignored.printStackTrace()
            return null
        }
    }

//    @RequestMapping(value = "/login", method = GET)
//    def login() {
//        def remote = new HTTPBuilder("http://localhost:8080/user")
//        remote.auth.basic('agent@company.com', 'password')
//        remote.request(Method.GET) {}
//        return new RedirectView("/offers")
//    }
}