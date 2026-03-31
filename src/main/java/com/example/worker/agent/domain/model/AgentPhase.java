package com.example.worker.agent.domain.model;

public enum AgentPhase {
    PLAN,    // /pdca plan   → docs/01-plan/features/{slug}.plan.md
    DESIGN,  // /pdca design → docs/02-design/features/{slug}.design.md
    DEVELOP  // /pdca do + /pdca analysis → PR
}
