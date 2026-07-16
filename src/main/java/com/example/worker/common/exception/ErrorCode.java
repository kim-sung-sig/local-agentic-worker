package com.example.worker.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다."),
    PROJECT_PATH_DUPLICATED("PROJECT_PATH_DUPLICATED", "이미 등록된 로컬 경로입니다."),
    PROJECT_PATH_INVALID("PROJECT_PATH_INVALID", "유효하지 않은 디렉토리 경로입니다."),

    PROJECT_REPOSITORY_URI_INVALID("PROJECT_REPOSITORY_URI_INVALID", "유효하지 않은 원격 Git 저장소 URI입니다."),
    PROJECT_REPOSITORY_URI_DUPLICATED("PROJECT_REPOSITORY_URI_DUPLICATED", "이미 등록된 원격 Git 저장소 URI입니다."),

    ISSUE_NOT_FOUND("ISSUE_NOT_FOUND", "이슈를 찾을 수 없습니다."),
    ISSUE_STATUS_TRANSITION_INVALID("ISSUE_STATUS_TRANSITION_INVALID", "허용되지 않는 상태 전이입니다."),

    WORKFLOW_RUN_NOT_FOUND("WORKFLOW_RUN_NOT_FOUND", "Workflow Run을 찾을 수 없습니다."),
    INVALID_STAGE_DECISION("INVALID_STAGE_DECISION", "유효하지 않은 단계 결정입니다.");

    private final String code;
    private final String message;

}
