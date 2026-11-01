package com.maxbenin.financeutils.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {

    List<LedgerEntry> findByDateBetween(LocalDate from, LocalDate to);

    List<LedgerEntry> findByMergedIntoIdIn(java.util.Collection<String> realIds);

    /** Manuais ainda não consolidados, candidatos a casar com um lançamento real. */
    @Query("""
            select e from LedgerEntry e
            where e.source = 'APP_MANUAL' and e.mergedIntoId is null and e.removedAt is null
              and e.date between :from and :to
            """)
    List<LedgerEntry> findOpenManuals(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Lançamentos que entram no painel: não removidos e não consolidados em outro. */
    @Query("""
            select e from LedgerEntry e
            where e.removedAt is null and e.mergedIntoId is null
              and e.date between :from and :to
            order by e.date desc
            """)
    List<LedgerEntry> findEffective(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
