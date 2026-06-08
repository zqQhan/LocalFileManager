-- Database initialization script
-- Executed on first PostgreSQL container startup

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS pg_trgm;      -- For fuzzy text search (ILIKE acceleration)
CREATE EXTENSION IF NOT EXISTS unaccent;     -- For accent-insensitive search
