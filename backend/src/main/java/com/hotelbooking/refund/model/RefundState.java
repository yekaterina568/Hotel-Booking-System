package com.hotelbooking.refund.model;

/** Имена констант совпадают с метками enum в PostgreSQL (регистр важен для Hibernate). */
public enum RefundState {
    Pending,
    Completed,
    Failed
}
