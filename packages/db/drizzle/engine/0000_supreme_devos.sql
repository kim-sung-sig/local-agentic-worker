CREATE SCHEMA "engine";
--> statement-breakpoint
CREATE TABLE "engine"."workflow_runs" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"ticket_id" uuid NOT NULL,
	"temporal_workflow_id" text NOT NULL,
	"current_stage" text,
	"status" text DEFAULT 'RUNNING' NOT NULL,
	"workspace_ref" text,
	"started_at" timestamp with time zone DEFAULT now() NOT NULL,
	"finished_at" timestamp with time zone,
	CONSTRAINT "workflow_runs_temporal_workflow_id_unique" UNIQUE("temporal_workflow_id")
);
--> statement-breakpoint
CREATE TABLE "engine"."stage_gates" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"workflow_run_id" uuid NOT NULL,
	"stage" text NOT NULL,
	"decision" text NOT NULL,
	"reason" text,
	"decided_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "engine"."attempt_records" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"workflow_run_id" uuid NOT NULL,
	"attempt_number" integer NOT NULL,
	"implementation_artifact_ref" text,
	"qa_report_ref" text,
	"qa_score" integer,
	"status" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"finished_at" timestamp with time zone,
	CONSTRAINT "attempt_records_workflow_run_id_attempt_number_unique" UNIQUE("workflow_run_id","attempt_number")
);
--> statement-breakpoint
ALTER TABLE "engine"."stage_gates" ADD CONSTRAINT "stage_gates_workflow_run_id_workflow_runs_id_fk" FOREIGN KEY ("workflow_run_id") REFERENCES "engine"."workflow_runs"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "engine"."attempt_records" ADD CONSTRAINT "attempt_records_workflow_run_id_workflow_runs_id_fk" FOREIGN KEY ("workflow_run_id") REFERENCES "engine"."workflow_runs"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "stage_gates_workflow_run_id_idx" ON "engine"."stage_gates" USING btree ("workflow_run_id");