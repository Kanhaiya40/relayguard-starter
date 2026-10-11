CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE workspace_memberships (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    user_id UUID NOT NULL REFERENCES app_users(id),
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_workspace_membership_user UNIQUE (workspace_id, user_id),
    CONSTRAINT ck_workspace_membership_role CHECK (role IN ('OWNER', 'MEMBER'))
);
CREATE INDEX idx_workspace_memberships_user ON workspace_memberships (user_id, workspace_id);

CREATE TABLE projects (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_project_workspace_name UNIQUE (workspace_id, name)
);

CREATE TABLE project_credentials (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    key_prefix VARCHAR(16) NOT NULL UNIQUE,
    credential_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_project_credentials_project ON project_credentials (project_id, revoked_at);
