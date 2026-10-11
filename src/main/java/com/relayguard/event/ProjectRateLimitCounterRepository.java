package com.relayguard.event;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRateLimitCounterRepository extends JpaRepository<ProjectRateLimitCounter, UUID> {}
