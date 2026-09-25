package com.chedidandrew.emeraldstandard.minecraft;

/** Runtime-only counter attached to loaded chunks; no save-format or network changes. */
public interface SurveyChunkRevision {
    long emeraldSurveyRevision();
}
