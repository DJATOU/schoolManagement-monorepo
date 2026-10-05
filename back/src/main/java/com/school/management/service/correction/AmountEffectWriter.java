package com.school.management.service.correction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Rédige l'effet d'une correction sur les montants, pour la Trace et le Journal (spec
 * admin-corrections, exigence 12.3, D8).
 *
 * <p>Une Série par segment, seuls les montants qui changent :
 * « Janvier (Math 1ère A) : versé 3 000,00 → 0,00 DA, reste 1 000,00 → 4 000,00 DA, à jour → en
 * retard ». Le texte est rédigé à l'écriture : il reste juste si la Série est renommée ou
 * supprimée ensuite.</p>
 *
 * <p>Les milliers sont séparés par une espace ordinaire, et non par l'espace fine insécable du
 * format français : le Journal s'imprime en PDF, et la police embarquée n'en a pas le glyphe.</p>
 */
final class AmountEffectWriter {

    private static final String ARROW = " → ";

    private AmountEffectWriter() {
    }

    /** Effet des changements donnés ; {@code null} si aucun montant ne change. */
    static String describe(List<SeriesAmountChange> changes) {
        if (changes.isEmpty()) {
            return null;
        }
        return changes.stream().map(AmountEffectWriter::describe).collect(Collectors.joining(" ; "));
    }

    private static String describe(SeriesAmountChange change) {
        AmountSnapshot before = change.before();
        AmountSnapshot after = change.after();
        List<String> parts = new ArrayList<>();
        amount(parts, "coût", before.cost(), after.cost());
        amount(parts, "dû à ce jour", before.dueSoFar(), after.dueSoFar());
        amount(parts, "versé", before.paid(), after.paid());
        amount(parts, "reste", before.remaining(), after.remaining());
        if (before.late() != after.late()) {
            parts.add(status(before.late()) + ARROW + status(after.late()));
        }
        return title(change) + " : " + String.join(", ", parts);
    }

    private static String title(SeriesAmountChange change) {
        String series = change.seriesName() == null ? "Série " + change.seriesId() : change.seriesName();
        return change.groupName() == null ? series : series + " (" + change.groupName() + ")";
    }

    private static void amount(List<String> parts, String label, BigDecimal before, BigDecimal after) {
        if (before.compareTo(after) != 0) {
            parts.add(label + " " + money(before) + ARROW + money(after) + " DA");
        }
    }

    private static String status(boolean late) {
        return late ? "en retard" : "à jour";
    }

    /** « 6 000,00 » : virgule décimale, espace ordinaire entre les milliers. */
    static String money(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(amount);
    }
}
