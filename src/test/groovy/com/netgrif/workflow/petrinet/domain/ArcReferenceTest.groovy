package com.netgrif.workflow.petrinet.domain

import com.netgrif.workflow.auth.service.interfaces.IUserService
import com.netgrif.workflow.importer.service.Importer
import com.netgrif.workflow.startup.ImportHelper
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension

@SuppressWarnings("GroovyAssignabilityCheck")
@ExtendWith(SpringExtension.class)
@ActiveProfiles(["test"])
@SpringBootTest
class ArcReferenceTest {

    public static final String NET_FILE = "arc_reference_test.xml"
    public static final String NET_TITLE = "arc_reference_test"
    public static final String NET_INITS = "a01"
    public static final String NET_INVALID_FILE = "arc_reference_invalid_test.xml"
    public static final String NET_INVALID_TITLE = "arc_reference_invalid_test"
    public static final String NET_INVALID_INITS = "a02"

    @Autowired
    private Importer importer

    @Autowired
    private ImportHelper helper

    @Autowired
    private IUserService userService

    private def stream = { String name ->
        return ArcOrderTest.getClassLoader().getResourceAsStream(name)
    }

    @Test
    void testReference() {
        def net = importer.importPetriNet(stream(NET_FILE)).get()

        assert net
    }

    @Test
    @Disabled("expected = IllegalArgumentException.class TODO:")
    void testInvalidReference() {
        importer.importPetriNet(stream(NET_INVALID_FILE)).get()
    }
}