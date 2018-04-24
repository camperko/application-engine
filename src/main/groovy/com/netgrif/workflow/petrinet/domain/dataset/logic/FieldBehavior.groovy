package com.netgrif.workflow.petrinet.domain.dataset.logic

enum FieldBehavior {
    REQUIRED("required"),
    OPTIONAL("optional"),
    VISIBLE("visible"),
    EDITABLE("editable"),
    HIDDEN("hidden"),
    FORBIDDEN("forbidden"),
    IMMEDIATE("immediate"),
    ANTONYM_SETUP("",true);


    private final String name
    private FieldBehavior[] antonyms

    FieldBehavior(String name) {
        this.name = name
    }

    FieldBehavior(String name, boolean populate) {
        this.name = name
        if (populate) {
            REQUIRED.setAntonyms()
            OPTIONAL.setAntonyms()
            VISIBLE.setAntonyms()
            EDITABLE.setAntonyms()
            HIDDEN.setAntonyms()
            FORBIDDEN.setAntonyms()
            IMMEDIATE.setAntonyms()
        }
    }

    private FieldBehavior[] addAntonyms() {
        switch (name) {
            case "required":
                return (FieldBehavior[]) [VISIBLE, HIDDEN, OPTIONAL, FORBIDDEN].toArray()
            case "optional":
                return (FieldBehavior[]) [REQUIRED, FORBIDDEN].toArray()
            case "visible":
                return (FieldBehavior[]) [REQUIRED, EDITABLE, HIDDEN, FORBIDDEN].toArray()
            case "editable":
                return (FieldBehavior[]) [VISIBLE, HIDDEN, FORBIDDEN].toArray()
            case "hidden":
                return (FieldBehavior[]) [EDITABLE, VISIBLE, REQUIRED, FORBIDDEN].toArray()
            case "forbidden":
                return (FieldBehavior[]) [VISIBLE, EDITABLE, HIDDEN, REQUIRED, OPTIONAL].toArray()
            case "immediate":
                return [] as FieldBehavior[]
            default:
                return null
        }
    }

    public static FieldBehavior fromString(String string) {
        return valueOf(string.toUpperCase());
    }

    FieldBehavior[] getAntonyms() {
        return antonyms
    }

    private void setAntonyms() {
        this.antonyms = addAntonyms()
    }

    public String toString() {
        return this.name
    }
}