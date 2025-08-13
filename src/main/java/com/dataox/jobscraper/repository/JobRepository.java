package com.dataox.jobscraper.repository;


import com.dataox.jobscraper.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;


public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {
    Optional<Job> findByJobUrl(String jobUrl);
    boolean existsByJobUrl(String jobUrl);
}