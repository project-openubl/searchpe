package io.github.projectopenubl.xhandler.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void ids_are_uuid_v7_and_time_ordered() throws InterruptedException {
        UUID first = Ids.newId();
        Thread.sleep(2);
        UUID second = Ids.newId();
        assertEquals(7, first.version());
        assertEquals(2, first.variant());
        assertTrue(first.toString().compareTo(second.toString()) < 0);
    }
}
