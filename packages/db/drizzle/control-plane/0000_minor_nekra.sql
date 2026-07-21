CREATE SCHEMA "control_plane";
--> statement-breakpoint
CREATE TYPE "control_plane"."document_kind" AS ENUM('PROMPT_TEMPLATE', 'DEVELOPMENT_GUIDE', 'QA_GUIDE', 'PLAN', 'IMPLEMENTATION_PLAN', 'DEVELOPMENT_RESULT', 'QA_REPORT');--> statement-breakpoint
CREATE TABLE "control_plane"."projects" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"name" text NOT NULL,
	"local_path" text,
	"base_branch" text DEFAULT 'main' NOT NULL,
	"repository_uri" text,
	"credential_ref" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "control_plane"."issues" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"project_id" uuid NOT NULL,
	"issue_number" integer NOT NULL,
	"title" text NOT NULL,
	"description" text,
	"priority" text,
	"status" text DEFAULT 'OPEN' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "issues_project_id_issue_number_unique" UNIQUE("project_id","issue_number")
);
--> statement-breakpoint
CREATE TABLE "control_plane"."documents" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"project_id" uuid NOT NULL,
	"issue_id" uuid,
	"kind" "control_plane"."document_kind" NOT NULL,
	"title" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "control_plane"."document_revisions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"document_id" uuid NOT NULL,
	"revision_number" integer NOT NULL,
	"content" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "document_revisions_document_id_revision_number_unique" UNIQUE("document_id","revision_number")
);
--> statement-breakpoint
CREATE TABLE "control_plane"."document_artifacts" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"document_id" uuid NOT NULL,
	"document_revision_id" uuid,
	"artifact_ref" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "control_plane"."notifications" (
	"id" bigserial PRIMARY KEY NOT NULL,
	"notification_id" uuid DEFAULT gen_random_uuid() NOT NULL,
	"event_key" text NOT NULL,
	"project_id" uuid NOT NULL,
	"workflow_run_id" uuid,
	"type" text NOT NULL,
	"severity" text NOT NULL,
	"publisher" text,
	"title" text NOT NULL,
	"message" text,
	"read_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "notifications_notification_id_unique" UNIQUE("notification_id"),
	CONSTRAINT "notifications_event_key_unique" UNIQUE("event_key")
);
--> statement-breakpoint
CREATE TABLE "control_plane"."users" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"email" text NOT NULL,
	"name" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "users_email_unique" UNIQUE("email")
);
--> statement-breakpoint
CREATE TABLE "control_plane"."memberships" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"project_id" uuid NOT NULL,
	"role" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "memberships_user_id_project_id_unique" UNIQUE("user_id","project_id")
);
--> statement-breakpoint
CREATE TABLE "control_plane"."sessions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"token" text NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "sessions_token_unique" UNIQUE("token")
);
--> statement-breakpoint
CREATE TABLE "control_plane"."outbox_events" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"aggregate_type" text NOT NULL,
	"aggregate_id" uuid NOT NULL,
	"event_type" text NOT NULL,
	"payload" jsonb NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"processed_at" timestamp with time zone
);
--> statement-breakpoint
ALTER TABLE "control_plane"."issues" ADD CONSTRAINT "issues_project_id_projects_id_fk" FOREIGN KEY ("project_id") REFERENCES "control_plane"."projects"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "control_plane"."documents" ADD CONSTRAINT "documents_project_id_projects_id_fk" FOREIGN KEY ("project_id") REFERENCES "control_plane"."projects"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "control_plane"."documents" ADD CONSTRAINT "documents_issue_id_issues_id_fk" FOREIGN KEY ("issue_id") REFERENCES "control_plane"."issues"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "control_plane"."document_revisions" ADD CONSTRAINT "document_revisions_document_id_documents_id_fk" FOREIGN KEY ("document_id") REFERENCES "control_plane"."documents"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "control_plane"."document_artifacts" ADD CONSTRAINT "document_artifacts_document_id_documents_id_fk" FOREIGN KEY ("document_id") REFERENCES "control_plane"."documents"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "control_plane"."document_artifacts" ADD CONSTRAINT "document_artifacts_document_revision_id_document_revisions_id_fk" FOREIGN KEY ("document_revision_id") REFERENCES "control_plane"."document_revisions"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "control_plane"."notifications" ADD CONSTRAINT "notifications_project_id_projects_id_fk" FOREIGN KEY ("project_id") REFERENCES "control_plane"."projects"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "control_plane"."memberships" ADD CONSTRAINT "memberships_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "control_plane"."users"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "control_plane"."memberships" ADD CONSTRAINT "memberships_project_id_projects_id_fk" FOREIGN KEY ("project_id") REFERENCES "control_plane"."projects"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "control_plane"."sessions" ADD CONSTRAINT "sessions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "control_plane"."users"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "projects_repository_uri_unique" ON "control_plane"."projects" USING btree ("repository_uri") WHERE "control_plane"."projects"."repository_uri" is not null;--> statement-breakpoint
CREATE INDEX "issues_project_id_idx" ON "control_plane"."issues" USING btree ("project_id");--> statement-breakpoint
CREATE INDEX "documents_project_id_idx" ON "control_plane"."documents" USING btree ("project_id");--> statement-breakpoint
CREATE INDEX "document_artifacts_document_id_idx" ON "control_plane"."document_artifacts" USING btree ("document_id");--> statement-breakpoint
CREATE INDEX "notifications_cursor_idx" ON "control_plane"."notifications" USING btree ("project_id","id");--> statement-breakpoint
CREATE INDEX "notifications_unread_idx" ON "control_plane"."notifications" USING btree ("project_id","read_at","id");