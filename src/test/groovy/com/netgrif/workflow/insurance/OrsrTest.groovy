package com.netgrif.workflow.insurance

import com.netgrif.workflow.premiuminsurance.IOrsrService
import com.netgrif.workflow.premiuminsurance.OrsrReference
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner.class)
@ActiveProfiles(["test"])
@SpringBootTest
class OrsrTest {

    @Autowired
    private IOrsrService service

    @Test
    void parseTest() {
        def ICO = 50_903_403 as String

        OrsrReference info = service.findByIco(ICO)

        assertCorrectValidOrsrInfo(info)
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private assertCorrectValidOrsrInfo(OrsrReference info) {
        assert info.name == "NETGRIF, s.r.o."
        assert info.city == "Bratislava - mestská časť Staré Mesto"
        assert info.zipCode == "811 07"
        assert info.street == "Blumentálska"
        assert info.streetNumber == "12"
    }
}