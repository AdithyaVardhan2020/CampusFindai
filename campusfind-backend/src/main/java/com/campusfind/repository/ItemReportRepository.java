package com.campusfind.repository;

import com.campusfind.entity.ItemReport;
import com.campusfind.entity.ReportStatus;
import com.campusfind.entity.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemReportRepository extends JpaRepository<ItemReport, Long> {

    List<ItemReport> findByType(ReportType type);

    List<ItemReport> findByStatus(ReportStatus status);

    List<ItemReport> findByTypeAndStatus(ReportType type, ReportStatus status);

    List<ItemReport> findByTypeAndStatusAndEmbeddingJsonIsNotNull(ReportType type, ReportStatus status);

    List<ItemReport> findByUserId(Long userId);

    List<ItemReport> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ItemReport> findAllByOrderByCreatedAtDesc();
}
