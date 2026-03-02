package com.webapp.configstore;

import java.util.List;

public record ConfigDocument(String name, List<ConfigField> fields) {}
