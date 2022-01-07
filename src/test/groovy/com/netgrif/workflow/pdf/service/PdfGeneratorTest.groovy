package com.netgrif.workflow.pdf.service

import com.netgrif.workflow.TestHelper
import com.netgrif.workflow.auth.service.UserService
import com.netgrif.workflow.importer.service.Importer
import com.netgrif.workflow.ipc.TaskApiTest
import com.netgrif.workflow.pdf.generator.config.PdfResource
import com.netgrif.workflow.pdf.generator.config.types.PdfPageNumberFormat
import com.netgrif.workflow.pdf.generator.domain.PdfField
import com.netgrif.workflow.pdf.generator.domain.PdfTextField
import com.netgrif.workflow.pdf.generator.service.interfaces.IPdfDataHelper
import com.netgrif.workflow.pdf.generator.service.interfaces.IPdfGenerator
import com.netgrif.workflow.petrinet.domain.DataGroup
import com.netgrif.workflow.petrinet.domain.PetriNet
import com.netgrif.workflow.petrinet.domain.VersionType
import com.netgrif.workflow.petrinet.domain.dataset.FieldType
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService
import com.netgrif.workflow.workflow.domain.Case
import com.netgrif.workflow.workflow.service.interfaces.IDataService
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner.class)
@ActiveProfiles(["test"])
@SpringBootTest
class PdfGeneratorTest {

    @Autowired
    private Importer importer

    @Autowired
    private IPdfGenerator pdfGenerator

    @Autowired
    private IWorkflowService workflowService

    @Autowired
    private UserService userService

    @Autowired
    private IPetriNetService petriNetService

    @Autowired
    private PdfResource pdfResource

    @Autowired
    private IDataService dataService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TestHelper testHelper

    public static final String[] TESTING_DATA = ["pdf_test_1.xml", "pdf_test_2.xml", "pdf_test_3.xml", "all_data_pdf.xml"]

    private def stream = { String name ->
        return TaskApiTest.getClassLoader().getResourceAsStream(name)
    }

    @Test
    void testPdfGenerator() {
        testHelper.truncateDbs()

        testingNormal()
        testingWithTemplate()
        testingLongDocument()
        testingPageNumber()
        testingCustomField()
        testAllData()
    }

    private void testAllData() {
        PdfResource pdfResource = applicationContext.getBean(PdfResource.class)
        Optional<PetriNet> net = petriNetService.importPetriNet(stream(TESTING_DATA[3]), VersionType.MAJOR, userService.getSystem().transformToLoggedUser())
        Case testCase = workflowService.createCase(net.get().getStringId(), "Test PDF", "", userService.getSystem().transformToLoggedUser())
        testCase.getPetriNet().getTransition("1").setDataGroups(getDataGroupMap(dataService.getDataGroups(testCase.getTasks()[0].getTask(), Locale.ENGLISH)))
        pdfResource.setOutputResource(new ClassPathResource("src/main/resources/out_" + TESTING_DATA[3] + "_.pdf"))
        pdfGenerator.setupPdfGenerator(pdfResource)
        pdfGenerator.generatePdf(testCase, "1", pdfResource)
    }

    private void testingNormal(){
        PdfResource pdfResource = applicationContext.getBean(PdfResource.class)
        Optional<PetriNet> net = petriNetService.importPetriNet(stream(TESTING_DATA[0]), "major", userService.getSystem().transformToLoggedUser())
        Case testCase = workflowService.createCase(net.get().getStringId(), "Test PDF", "", userService.getSystem().transformToLoggedUser())
        testCase.getPetriNet().getTransition("1").setDataGroups(getDataGroupMap(dataService.getDataGroups(testCase.getTasks()[0].getTask(), Locale.ENGLISH)))
        pdfResource.setOutputResource(new ClassPathResource("src/main/resources/out_" + TESTING_DATA[0] + "_.pdf"))
        pdfGenerator.setupPdfGenerator(pdfResource)
        pdfGenerator.generatePdf(testCase, "1", pdfResource)
    }

    private void testingWithTemplate(){
        PdfResource pdfResource = applicationContext.getBean(PdfResource.class)
        Optional<PetriNet> net = petriNetService.importPetriNet(stream(TESTING_DATA[1]), "major", userService.getSystem().transformToLoggedUser())
        Case testCase = workflowService.createCase(net.get().getStringId(), "Test PDF", "", userService.getSystem().transformToLoggedUser())
        testCase.getPetriNet().getTransition("1").setDataGroups(getDataGroupMap(dataService.getDataGroups(testCase.getTasks()[0].getTask(), Locale.ENGLISH)))
        pdfResource.setOutputResource(new ClassPathResource("src/main/resources/out_" + TESTING_DATA[1] + "_.pdf"))
        pdfResource.setMarginLeft(75)
        pdfResource.setMarginRight(75)
        pdfResource.setMarginTitle(100)
        pdfResource.updateProperties()
        pdfResource.setTemplateResource(new ClassPathResource("src/main/resources/pdfGenerator/existing.pdf"))
        pdfGenerator.setupPdfGenerator(pdfResource)
        pdfGenerator.generatePdf(testCase, "1", pdfResource)
    }

    private void testingLongDocument(){
        PdfResource pdfResource = applicationContext.getBean(PdfResource.class)
        Optional<PetriNet> net = petriNetService.importPetriNet(stream(TESTING_DATA[2]), "major", userService.getSystem().transformToLoggedUser())
        Case testCase = workflowService.createCase(net.get().getStringId(), "Test PDF", "", userService.getSystem().transformToLoggedUser())
        testCase.getPetriNet().getTransition("1").setDataGroups(getDataGroupMap(dataService.getDataGroups(testCase.getTasks()[0].getTask(), Locale.ENGLISH)))
        pdfResource.setOutputResource(new ClassPathResource("src/main/resources/out_" + TESTING_DATA[2] + "_.pdf"))
        pdfGenerator.setupPdfGenerator(pdfResource)
        pdfGenerator.generatePdf(testCase, "1", pdfResource)
    }

    private void testingPageNumber(){
        PdfResource pdfResource = applicationContext.getBean(PdfResource.class)
        Optional<PetriNet> net = petriNetService.importPetriNet(stream(TESTING_DATA[2]), "major", userService.getSystem().transformToLoggedUser())
        Case testCase = workflowService.createCase(net.get().getStringId(), "Test PDF", "", userService.getSystem().transformToLoggedUser())
        testCase.getPetriNet().getTransition("1").setDataGroups(getDataGroupMap(dataService.getDataGroups(testCase.getTasks()[0].getTask(), Locale.ENGLISH)))
        pdfResource.setNumberFormat(Locale.US)
        pdfResource.setPageNumberPosition(pdfResource.getMarginLeft())
        pdfResource.setPageNumberFormat(PdfPageNumberFormat.SIMPLE)
        pdfResource.setOutputResource(new ClassPathResource("src/main/resources/out_page_number_.pdf"))
        pdfGenerator.setupPdfGenerator(pdfResource)
        pdfGenerator.generatePdf(testCase, "1", pdfResource)
    }

    private void testingCustomField(){
        PdfResource pdfResource = applicationContext.getBean(PdfResource.class)
        PdfField pdf = new PdfTextField("footer_company_title",
                null,
                "Netgrif Application Engine",
                FieldType.TEXT,
                pdfResource.getMarginLeft(),
                pdfResource.getPageHeight() - pdfResource.getMarginBottom(),
                (int) (pdfResource.getPageDrawableWidth() / pdfResource.getFormGridCols()),
                pdfResource.getLineHeight(),
                pdfResource)

        Optional<PetriNet> net = petriNetService.importPetriNet(stream(TESTING_DATA[2]), "major", userService.getSystem().transformToLoggedUser())
        Case testCase = workflowService.createCase(net.get().getStringId(), "Test PDF", "", userService.getSystem().transformToLoggedUser())
        testCase.getPetriNet().getTransition("1").setDataGroups(getDataGroupMap(dataService.getDataGroups(testCase.getTasks()[0].getTask(), Locale.ENGLISH)))
        pdfResource.setOutputResource(new ClassPathResource("src/main/resources/out_custom_field.pdf"))
        pdfGenerator.setupPdfGenerator(pdfResource)
        pdfGenerator.addCustomField(pdf, pdfResource)
        pdfGenerator.generatePdf(testCase, "1", pdfResource)
    }

    private Map<String, DataGroup> getDataGroupMap(List<DataGroup> dataGroupList){
        Map<String, DataGroup> dataGroupMap = new HashMap<>()
        dataGroupList.each {
            dataGroupMap.put(it.stringId, it)
        }
        return dataGroupMap
    }


}
