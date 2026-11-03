package com.maxbenin.financeutils.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {

    List<LedgerEntry> findByDateBetween(LocalDate from, LocalDate to);

    List<LedgerEntry> findByMergedIntoIdIn(Collection<String> realIds);

    /** Manuais (do app ou do Dinzo) ainda não consolidados, candidatos a casar com um lançamento real. */
    @Query("""
            select e from LedgerEntry e
            where e.manual = true and e.mergedIntoId is null and e.removedAt is null
              and e.date between :from and :to
            """)
    List<LedgerEntry> findOpenManuals(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Lançamentos que valem: não removidos e não consolidados em outro. */
    @Query("""
            select e from LedgerEntry e
            where e.removedAt is null and e.mergedIntoId is null
              and e.date between :from and :to
            order by e.date desc, e.createdAt desc
            """)
    List<LedgerEntry> findEffective(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
