package com.fmworkflow.petrinet.web;

import com.fmworkflow.json.JsonBuilder;
import com.fmworkflow.petrinet.domain.PetriNet;
import com.fmworkflow.petrinet.service.IPetriNetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/petrinet")
public class PetriNetController {
    @Autowired
    private IPetriNetService service;

    @RequestMapping(value = "/import", method = RequestMethod.POST)
    public String importPetriNet(@RequestBody ImportBody body) {
        try {
            service.importPetriNet(body.xmlFile, body.title);
            return JsonBuilder.successMessage("Petri net imported successfully");
        } catch (SAXException e) {
            e.printStackTrace();
            return JsonBuilder.errorMessage("Invalid xml file");
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            return JsonBuilder.errorMessage("Invalid parser configuration");
        } catch (IOException e) {
            e.printStackTrace();
            return JsonBuilder.errorMessage("IO error");
        }
    }

    @RequestMapping(value = "/all", method = RequestMethod.GET)
    public List<PetriNet> getAll() {
        return service.loadAll();
    }

    class ImportBody {
        public File xmlFile;
        public String title;

        public ImportBody() {}
    }
}
