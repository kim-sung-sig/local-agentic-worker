package com.example.worker.issue.application.port;

import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.domain.model.IssueId;
import com.example.worker.project.domain.model.ProjectId;

import java.util.List;
import java.util.Optional;

public interface IssueRepository {

    Issue save(Issue issue);

    Optional<Issue> findById(IssueId id);

    List<Issue> findByProjectId(ProjectId projectId);

    int findMaxIssueNumber(ProjectId projectId);
}
