package com.distribuidora.backend.dto;

import com.distribuidora.backend.model.Category;

public class CategoryResponse {

    private String slug;
    private String name;
    private String icon;

    public static CategoryResponse from(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.slug = category.getSlug();
        response.name = category.getName();
        response.icon = category.getIcon();
        return response;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }
}
