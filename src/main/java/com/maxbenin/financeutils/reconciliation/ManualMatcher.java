package com.maxbenin.financeutils.reconciliation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Procura, entre os lançamentos manuais em aberto, o que corresponde a um lançamento
 * real recém-chegado do Open Finance.
 *
 * <p>Critérios obrigatórios: mesma conta, mesma direção, valor dentro da tolerância e
 * data do real entre 1 dia antes e {@code maxDaysAfter} dias depois do manual.
 * A pontuação combina proximidade de valor, de data e semelhança da descrição.
 */
public final class ManualMatcher {

    public record Entry(String id, String accountId, String direction, BigDecimal amount,
                        LocalDate date, String description) {
    }

    public record Match(String manualId, double score, boolean automatic) {
    }

    private final BigDecimal absTolerance;
    private final BigDecimal pctTolerance;
    private final int maxDaysAfter;
    private final double autoThreshold;
    private final double autoMargin;

    public ManualMatcher(BigDecimal absTolerance, BigDecimal pctTolerance, int maxDaysAfter,
                         double autoThreshold, double autoMargin) {
        this.absTolerance = absTolerance;
        this.pctTolerance = pctTolerance;
        this.maxDaysAfter = maxDaysAfter;
        this.autoThreshold = autoThreshold;
        this.autoMargin = autoMargin;
    }

    public static ManualMatcher defaults() {
        return new ManualMatcher(new BigDecimal("0.50"), new BigDecimal("0.02"), 3, 0.6, 0.15);
    }

    /** Melhor candidato; {@code automatic=false} significa "precisa de confirmação". */
    public Optional<Match> findMatch(Entry real, Collection<Entry> manuals) {
        List<Match> scored = new ArrayList<>();
        for (Entry m : manuals) {
            score(real, m).ifPresent(s -> scored.add(new Match(m.id(), s, false)));
        }
        if (scored.isEmpty()) {
            return Optional.empty();
        }
        scored.sort(Comparator.comparingDouble(Match::score).reversed());
        Match best = scored.get(0);
        double second = scored.size() > 1 ? scored.get(1).score() : 0;
        boolean auto = best.score() >= autoThreshold && best.score() - second >= autoMargin;
        return Optional.of(new Match(best.manualId(), round(best.score()), auto));
    }

    Optional<Double> score(Entry real, Entry manual) {
        if (!Objects.equals(real.accountId(), manual.accountId())
                || !Objects.equals(real.direction(), manual.direction())) {
            return Optional.empty();
        }
        long days = ChronoUnit.DAYS.between(manual.date(), real.date());
        if (days < -1 || days > maxDaysAfter) {
            return Optional.empty();
        }
        BigDecimal diff = real.amount().subtract(manual.amount()).abs();
        BigDecimal tolerance = absTolerance.max(manual.amount().multiply(pctTolerance));
        if (diff.compareTo(tolerance) > 0) {
            return Optional.empty();
        }
        double amountScore = 1 - diff.divide(tolerance, 6, RoundingMode.HALF_UP).doubleValue();
        double dateScore = 1 - Math.abs(days) / (double) (maxDaysAfter + 1);
        double descScore = similarity(real.description(), manual.description());
        return Optional.of(0.5 * amountScore + 0.3 * dateScore + 0.2 * descScore);
    }

    /** Jaccard entre palavras (≥3 letras) das descrições, sem acentos. */
    static double similarity(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return inter.size() / (double) union.size();
    }

    private static Set<String> tokens(String s) {
        if (s == null) {
            return Set.of();
        }
        String norm = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        Set<String> out = new HashSet<>();
        for (String t : norm.split("[^A-Z0-9]+")) {
            if (t.length() >= 3) {
                out.add(t);
            }
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
