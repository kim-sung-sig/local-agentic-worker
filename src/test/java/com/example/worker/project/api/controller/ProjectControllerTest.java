package com.example.worker.project.api.controller;

import com.example.worker.project.application.service.ProjectCommandService;
import com.example.worker.project.application.service.ProjectQueryService;
import com.example.worker.project.domain.model.ProjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
@DisplayName("ProjectController")
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectCommandService commandService;

    @MockitoBean
    private ProjectQueryService queryService;

    @Test
    @DisplayName("기존 단수 프로젝트 등록 경로도 201을 반환한다")
    void register_legacySingularPath_returnsCreated() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(commandService.registerProject(any())).thenReturn(ProjectId.of(projectId));

        mockMvc.perform(post("/api/project")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "catalog",
                                  "repositoryUri": "https://github.com/acme/catalog.git",
                                  "baseBranch": "main"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/projects/" + projectId));
    }
}
