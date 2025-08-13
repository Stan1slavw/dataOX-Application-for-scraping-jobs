package com.dataox.jobscraper.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "jobs", uniqueConstraints = {@UniqueConstraint(columnNames = {"job_url"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "position_name", columnDefinition = "text")
    private String positionName;

    @Column(name = "organization_url", columnDefinition = "text")
    private String organizationUrl;

    @Column(name = "logo_url", columnDefinition = "text")
    private String logoUrl;

    @Column(name = "organization_title", columnDefinition = "text")
    private String organizationTitle;

    @Column(name = "labor_function", columnDefinition = "text")
    private String laborFunction;

    @Column(name = "location", columnDefinition = "text")
    private String location;

    @Column(name = "posted_date")
    private Long postedDate;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "job_tags", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "tag", columnDefinition = "text")
    private List<String> tags = new ArrayList<>();

    @Column(name = "job_url", columnDefinition = "text", nullable = false)
    private String jobUrl;

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

}
