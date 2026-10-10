package com.campusfind.entity;

public enum Role {
    ROLE_STUDENT,
    ROLE_ADMIN;

    public String getAuthorityName() {
        return name();
    }
}
