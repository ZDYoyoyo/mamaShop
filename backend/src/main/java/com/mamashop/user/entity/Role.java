package com.mamashop.user.entity;

/** 會員角色。資料庫存字串（users.role），新增值時要同步修改 migration 的 CHECK 限制。 */
public enum Role {
    USER,
    ADMIN
}
