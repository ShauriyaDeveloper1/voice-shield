-- Spam Reports Table for community-based spam detection
-- When a phone number gets 20+ reports from different users, it's globally marked as spam

CREATE TABLE IF NOT EXISTS spam_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_user_id TEXT NOT NULL,
    reported_phone TEXT NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(reporter_user_id, reported_phone)
);

-- Index for fast lookup by reported phone number
CREATE INDEX IF NOT EXISTS idx_spam_reports_phone ON spam_reports(reported_phone);

-- Enable RLS
ALTER TABLE spam_reports ENABLE ROW LEVEL SECURITY;

-- Allow anonymous inserts and reads (the app validates auth via its own token)
CREATE POLICY "Allow public insert on spam_reports"
    ON spam_reports FOR INSERT
    WITH CHECK (true);

CREATE POLICY "Allow public read on spam_reports"
    ON spam_reports FOR SELECT
    USING (true);
