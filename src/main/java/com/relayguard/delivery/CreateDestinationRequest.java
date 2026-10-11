package com.relayguard.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDestinationRequest(@NotBlank @Size(max = 2048) String url) {}
