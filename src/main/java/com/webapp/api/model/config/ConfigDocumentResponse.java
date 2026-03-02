package com.webapp.api.model.config;

import java.util.List;

public record ConfigDocumentResponse(String name, List<ConfigFieldResponse> fields) {}
