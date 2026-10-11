package com.relayguard.event;

import java.util.List;

public record EventListResponse(List<EventSummaryResponse> items, int page, int size, boolean hasNext) {}
