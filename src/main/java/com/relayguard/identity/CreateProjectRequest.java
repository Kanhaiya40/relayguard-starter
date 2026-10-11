package com.relayguard.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(@NotBlank @Size(max = 120) String name) {}
