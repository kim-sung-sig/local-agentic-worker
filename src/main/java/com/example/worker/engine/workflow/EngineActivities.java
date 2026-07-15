package com.example.worker.engine.workflow;

import com.example.worker.engine.application.contract.v1.AttemptHistoryRequest;
import com.example.worker.engine.application.contract.v1.AttemptHistoryResponse;
import com.example.worker.engine.application.contract.v1.ImplementationRequest;
import com.example.worker.engine.application.contract.v1.ImplementationResponse;
import com.example.worker.engine.application.contract.v1.NotificationRequest;
import com.example.worker.engine.application.contract.v1.NotificationResponse;
import com.example.worker.engine.application.contract.v1.PlanningRequest;
import com.example.worker.engine.application.contract.v1.PlanningResponse;
import com.example.worker.engine.application.contract.v1.QaRequest;
import com.example.worker.engine.application.contract.v1.QaResult;
import com.example.worker.engine.application.contract.v1.SourceControlRequest;
import com.example.worker.engine.application.contract.v1.SourceControlResponse;
import com.example.worker.engine.application.contract.v1.TicketAssessmentRequest;
import com.example.worker.engine.application.contract.v1.TicketAssessmentResponse;
import com.example.worker.engine.application.contract.v1.WorkspaceRequest;
import com.example.worker.engine.application.contract.v1.WorkspaceResponse;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface EngineActivities {

    @ActivityMethod
    TicketAssessmentResponse assessTicket(TicketAssessmentRequest request);

    @ActivityMethod
    PlanningResponse planImplementation(PlanningRequest request);

    @ActivityMethod
    WorkspaceResponse prepareWorkspace(WorkspaceRequest request);

    @ActivityMethod
    ImplementationResponse implement(ImplementationRequest request);

    @ActivityMethod
    QaResult runQualityAssurance(QaRequest request);

    @ActivityMethod
    AttemptHistoryResponse recordAttemptHistory(AttemptHistoryRequest request);

    @ActivityMethod
    SourceControlResponse manageSourceControl(SourceControlRequest request);

    @ActivityMethod
    NotificationResponse sendNotification(NotificationRequest request);
}
