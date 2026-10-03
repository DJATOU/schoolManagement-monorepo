package com.school.management.service.correction;

import java.util.List;

/**
 * Une table de traces, lue pour le Journal d'un élève (D8). Chaque source rédige ses entrées en
 * français ; le Journal les fusionne et les ordonne.
 */
interface JournalSource {

    /** Les entrées d'un élève, dans un ordre quelconque. */
    List<Item> itemsOf(Long studentId);

    /**
     * Une entrée et son rang dans sa table, qui départage deux entrées de même horodatage : les
     * Traces d'une même correction sont écrites dans la même milliseconde.
     */
    record Item(JournalEntry entry, long rank) {
    }
}
