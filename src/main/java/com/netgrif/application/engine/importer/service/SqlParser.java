package com.netgrif.application.engine.importer.service;

import com.netgrif.application.engine.importer.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SqlParser {

    public static Document createFromSql(String identifier, String initials, I18NStringType title, String icon,
                                         I18NStringTypeWithExpression caseName, Map<String, String> dataMap) {
        Document sqlPetriNet = createNetWithOptions(identifier, initials, title, icon, caseName);
        dataMap.forEach((key, value) -> sqlPetriNet.getData().add(createDataField(key, value)));
        Data deleteWarning = createDataField("delete_warning", "delete_warning");
        // TODO: set deleteWarning value + translations
        sqlPetriNet.getData().add(deleteWarning);
        createTransitions(dataMap.values()).forEach(transition -> sqlPetriNet.getTransition().add(transition));
        sqlPetriNet.getPlace().add(createPlace());
        createArcs().forEach(arc -> sqlPetriNet.getArc().add(arc));
        return sqlPetriNet;
    }

    private static Document createNetWithOptions(String identifier, String initials, I18NStringType title, String icon,
                                      I18NStringTypeWithExpression caseName) {
        Document sqlPetriNet = new Document();
        sqlPetriNet.setId(identifier);
        sqlPetriNet.setInitials(initials);
        sqlPetriNet.setTitle(title);
        sqlPetriNet.setIcon(icon);
        sqlPetriNet.setDefaultRole(true);
        sqlPetriNet.setAnonymousRole(true);
        sqlPetriNet.setTransitionRole(true);
        sqlPetriNet.setCaseName(caseName);
        return sqlPetriNet;
    }

    private static Data createDataField(String fieldName, String fieldType) {
        Data dataField = new Data();
        dataField.setType(resolveType(fieldType));
        dataField.setId(fieldName);
        dataField.setTitle(createI18nStringType(fieldName.substring(0, 1).toUpperCase() + fieldType.substring(1), null));
        return dataField;
    }

    private static List<Transition> createTransitions(Collection<String> dataMap) {
        List<Transition> transitionList = new ArrayList<>();
        // TODO: create all three transitions
//        transitionList.add()
        return transitionList;
    }

    private static Transition createTransition(String id, int x, int y, String label, String icon,
                                               Collection<String> dataMap) {
        Transition transition = new Transition();
        transition.setId(id);
        transition.setX(x);
        transition.setY(y);
        transition.setLabel(createI18nStringType(label, null));
        transition.setIcon(icon);
        transition.setAssignPolicy(AssignPolicy.AUTO);
        DataGroup dataGroup = new DataGroup();
        dataGroup.setId(id + "_group");
        dataGroup.setCols(4);
        dataGroup.setLayout(LayoutType.GRID);
        // TODO: add data to dataGroup
        if (dataMap != null) {

        } else {

        }
        return transition;
    }

    private static Place createPlace() {
        Place place = new Place();
        place.setId("place");
        place.setX(460);
        place.setX(260);
        place.setTokens(1);
        place.setStatic(false);
        return place;
    }

    private static List<Arc> createArcs() {
        List<Arc> arcList = new ArrayList<>();
        arcList.add(createArc("arcRead", ArcType.READ, "place", "read", null, null));
        arcList.add(createArc("arcDelete", ArcType.REGULAR, "place", "delete", null, null));
        arcList.add(createArc("arcUpdateAssign", ArcType.REGULAR, "place", "update", 420, 300));
        arcList.add(createArc("arcUpdateFinish", ArcType.REGULAR, "update", "place", 500, 300));
        return arcList;
    }

    private static Arc createArc(String id, ArcType type, String sourceId, String destinationId,
                                 Integer x, Integer y) {
        Arc arc = new Arc();
        arc.setId(id);
        arc.setType(type);
        arc.setSourceId(sourceId);
        arc.setDestinationId(destinationId);
        arc.setMultiplicity(1);
        if (x != null) {
            Breakpoint breakpoint = new Breakpoint();
            breakpoint.setX(x);
            breakpoint.setY(y);
            arc.getBreakpoint().add(breakpoint);
        }
        return arc;
    }

    private static DataType resolveType(String fieldType) {
        // TODO: add more data types from different sqls (currently just Oracle added)
        // TODO: maybe this method will need to be split to multiple methods depending on SQL language
        if (fieldType.contains("delete_warning")) {
            return DataType.I_18_N;
        }
        if (fieldType.contains("CHAR")) {
            return DataType.TEXT;
        }
        if (fieldType.contains("CLOB") || fieldType.contains("BLOB") || fieldType.contains("LONG")
                || fieldType.contains("BFILE") || fieldType.contains("RAW")) {
            return DataType.FILE;
        }
        if (fieldType.contains("DATE") || fieldType.contains("TIMESTAMP")) {
            return DataType.DATE_TIME;
        }
        if (fieldType.contains("NUMBER") || fieldType.contains("FLOAT") || fieldType.contains("BINARY_")) {
            return DataType.NUMBER;
        }
        return DataType.TEXT;
    }

    private static I18NStringType createI18nStringType(String value, String name) {
        I18NStringType i18NStringType = new I18NStringType();
        i18NStringType.setValue(value);
        i18NStringType.setName(name);
        return i18NStringType;
    }

}
