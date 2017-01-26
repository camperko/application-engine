package com.fmworkflow.petrinet.service;

import com.fmworkflow.Persistable;
import com.fmworkflow.petrinet.domain.Arc;
import com.fmworkflow.petrinet.domain.Node;
import com.fmworkflow.petrinet.domain.Place;
import com.fmworkflow.petrinet.domain.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.HashMap;
import java.util.Map;

public class ImportHandler extends DefaultHandler {
    private final Logger log = LoggerFactory.getLogger(ImportHandler.class);

    private Map<Integer, Node> nodes;
    private Element element;
    private Persistable object;

    public ImportHandler() {
        nodes = new HashMap<>();
    }

    @Override
    public void startDocument() throws SAXException {
        log.debug("Parsing started");
    }

    @Override
    public void endDocument() throws SAXException {
        log.debug("Parsing ended");
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        element = Element.fromString(qName);
        switch (element) {
            case PLACE:
                object = new Place();
                break;
            case TRANSITION:
                object = new Transition();
                break;
            case ARC:
                object = new Arc();
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        element = Element.fromString(qName);
        switch (element) {
            case PLACE:
            case TRANSITION:
            case ARC:
                object.persist();
        }
        element = Element.DOCUMENT;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        String characters = new String(ch, start, length);
        switch (element) {
            case SOURCEID:
                Node source = getNodeWithId(characters);
                ((Arc) object).setSource(source);
                break;
            case DESTINATIONID:
                Node destination = getNodeWithId(characters);
                ((Arc) object).setDestination(destination);
                break;
            case ID:
                if (object.getClass() != Arc.class)
                    setNodeWithId(characters);
                break;
            case MULTIPLICITY:
                Integer multiplicity = Integer.parseInt(characters);
                ((Arc) object).setMultiplicity(multiplicity);
                break;
            case TOKENS:
                Integer tokens = Integer.parseInt(characters);
                ((Place) object).setTokens(tokens);
                break;
            case X:
                Integer x = Integer.parseInt(characters);
                ((Node) object).setPositionX(x);
                break;
            case Y:
                Integer y = Integer.parseInt(characters);
                ((Node) object).setPositionY(y);
                break;
            case LABEL:
                ((Node) object).setTitle(characters);
                break;
            case STATIC:
                Boolean isStatic = Boolean.parseBoolean(characters);
                ((Place) object).setStatic(isStatic);
                break;
            case TYPE:
                // TODO: 26. 1. 2017
            default:
        }
    }

    private void setNodeWithId(String idString) {
        Integer id = Integer.parseInt(idString);
        nodes.put(id, (Node) object);
    }

    private Node getNodeWithId(String idString) {
        Integer id = Integer.parseInt(idString);
        return nodes.get(id);
    }

    enum Element {
        DOCUMENT ("document"),
        PLACE ("place"),
        TRANSITION ("transition"),
        ARC ("arc"),
        ID ("id"),
        X ("x"),
        Y ("y"),
        LABEL ("label"),
        SOURCEID ("sourceId"),
        DESTINATIONID ("destinationId"),
        MULTIPLICITY ("multiplicity"),
        TOKENS ("tokens"),
        STATIC ("static"),
        TYPE ("type");

        String name;

        Element(String name) {
            this.name = name;
        }

        public static Element fromString(String name) {
            return Element.valueOf(name.toUpperCase());
        }
    }
}