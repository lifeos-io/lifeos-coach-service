-- Coach knows the user from Strategy's events (never by calling Strategy), proposes strategy revisions and
-- talks with the user. It changes nothing in the plan: a proposal waits for the user's decision in Strategy.

-- The user's situation, one row per fact (a goal, the current strategy of a goal, a knowledge item, the active
-- week, ...), as the last event about it described it. Full-state events with a version replace older ones only.
CREATE TABLE context_fact (
    user_id    uuid        NOT NULL,
    kind       text        NOT NULL CHECK (kind IN ('PROFILE', 'GOAL', 'STRATEGY', 'KNOWLEDGE_ITEM', 'WEEK',
        'EVIDENCE', 'FEEDBACK', 'DECISION', 'IDEA', 'WEEKLY_REVIEW')),
    subject_id uuid        NOT NULL,
    version    integer,
    data       jsonb       NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, kind, subject_id)
);

-- What Coach proposed (RecommendationProposed) and what became of it (Strategy's ProposalDecided).
CREATE TABLE recommendation (
    id                  uuid        PRIMARY KEY,
    user_id             uuid        NOT NULL,
    goal_id             uuid        NOT NULL,
    -- What prompted it.
    reason              text        NOT NULL CHECK (reason IN ('FEEDBACK', 'WEEKLY_REVIEW', 'DRIFT')),
    based_on_version_no integer     CHECK (based_on_version_no >= 1),
    rationale           text        NOT NULL CHECK (char_length(rationale) BETWEEN 1 AND 2000),
    revision            jsonb       NOT NULL,
    status              text        NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'SUPERSEDED')),
    decision_note       text        CHECK (char_length(decision_note) <= 1000),
    decided_at          timestamptz,
    version             integer     NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    CHECK ((status = 'PENDING') = (decided_at IS NULL))
);

-- "May Coach propose for this goal now?" (no pending proposal, none in the last days).
CREATE INDEX recommendation_goal_idx ON recommendation (goal_id, created_at DESC);
CREATE INDEX recommendation_user_idx ON recommendation (user_id);

-- Chats with Coach.
CREATE TABLE conversation (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL,
    title      text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX conversation_user_idx ON conversation (user_id, updated_at DESC);

CREATE TABLE message (
    id              uuid        PRIMARY KEY,
    conversation_id uuid        NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    user_id         uuid        NOT NULL,
    role            text        NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content         text        NOT NULL CHECK (char_length(content) BETWEEN 1 AND 20000),
    created_at      timestamptz NOT NULL
);

CREATE INDEX message_conversation_idx ON message (conversation_id, created_at);
