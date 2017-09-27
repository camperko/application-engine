package com.netgrif.workflow.pdf.service

import com.netgrif.workflow.utils.ResourceFileLoader
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentCatalog
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PdfUtils {

    private static final Logger log = LoggerFactory.getLogger(PdfUtils.class)

    static File removePages(File pdfFile, int... pages) {
        PDDocument document = PDDocument.load(pdfFile)

        pages.each {
            document.removePage(it)
        }
        document.save(pdfFile)
        document.close()

        return pdfFile
    }

    private static final int KEY_LENGTH = 128

    static File encryptPdfFile(String outPdfPath, File input, String ownerPassword = "", String userPassword = "") {
        PDDocument doc = PDDocument.load(input)
        AccessPermission ap = new AccessPermission(canFillInForm: false, canModify: false)
        StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPassword, userPassword, ap)
        File encrypted = new File(outPdfPath)

        spp.setEncryptionKeyLength(KEY_LENGTH)
        doc.protect(spp)
        doc.save(encrypted)
        doc.close()

        return encrypted
    }

    static File mergePdfFiles(String outPdfName, File... files) {
        PDFMergerUtility pdfMerger = new PDFMergerUtility()
        pdfMerger.setDestinationFileName(outPdfName)

        files.each {
            pdfMerger.addSource(it)
        }

        pdfMerger.mergeDocuments(MemoryUsageSetting.setupMixed(100_000_000L, 500_000_000L))

        return new File(pdfMerger.getDestinationFileName())
    }

    static File fillPdfForm(String outPdfName, InputStream pdfFile, InputStream xmlFile) throws IllegalArgumentException {
        fillPdfForm(outPdfName, pdfFile, xmlFile.getText())
    }

    static File fillPdfForm(String outPdfName, InputStream pdfFile, String xml) throws IllegalArgumentException {
        try {
            PDDocument document = PDDocument.load(pdfFile)
            PDDocumentCatalog docCatalog = document.getDocumentCatalog()
            PDAcroForm acroForm = docCatalog.getAcroForm()

            Map<String, String> fonts = new HashMap<>()
            fonts.put("/KlavikaBasic-Regular", addFont(document, acroForm, "classpath:fonts/Klavika Regular.ttf"))
            fonts.put("/KlavikaBasic-Bold", addFont(document, acroForm, "classpath:fonts/Klavika Bold.ttf"))
            fonts.put("/KlavikaBasic-Medium", addFont(document, acroForm, "classpath:fonts/Klavika Medium.ttf"))
            addFieldValues(acroForm, xml, fonts)
            return saveToFile(document, outPdfName)
        } catch (IOException e) {
            e.printStackTrace()
            throw new IllegalArgumentException(e)
        }
    }

    private static String addFont(PDDocument document, PDAcroForm acroForm, String fontPath) {
        PDResources res = acroForm.getDefaultResources()
        if (res == null)
            res = new PDResources()

        File fontFile = ResourceFileLoader.loadResourceFile(fontPath)
        PDType0Font font = PDType0Font.load(document, new FileInputStream(fontFile), true)

        String fontName = res.add(font).name
        if (fontName == null)
            log.error("Could not add font to pdf resource")

        acroForm.setDefaultResources(res)

        return fontName
    }

    private static void addFieldValues(PDAcroForm acroForm, String xmlText, Map<String, String> fonts) {
        def fieldValues = new XmlSlurper().parseText(xmlText)

        fieldValues.children().each {
            setFieldValueAndFont(acroForm, it, fonts)
        }

        acroForm.flatten()
    }

    private static setFieldValueAndFont(PDAcroForm acroForm, def xmlNode, Map<String, String> fonts) {
        def id = ((xmlNode["@xfdf:original"] as String) ?: xmlNode.name()) as String
        def field = acroForm.fieldIterator.find { it.partialName.equalsIgnoreCase(id) }
        if (field == null) {
            log.error("Cannot find field [$id]")
            return
        }

        try {
            String DA = field.getCOSObject().getString(COSName.DA)

            fonts.each { font ->
                if (DA.contains(font.key))
                    field.getCOSObject().setString(COSName.DA, DA.replaceAll(font.key, "/${font.value}"))
            }
            field.setValue(xmlNode as String)
        } catch (NullPointerException e) {
            log.error("Cannot find field $id", e)
        }
    }

    private static File saveToFile(PDDocument document, String outPdfName) {
        File file = new File(outPdfName)
        document.save(file)
        document.close()
        return file
    }
}