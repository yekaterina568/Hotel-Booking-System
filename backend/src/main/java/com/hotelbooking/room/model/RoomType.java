package com.hotelbooking.room.model;

/** Имена констант совпадают с метками enum в PostgreSQL (регистр важен для Hibernate). */
public enum RoomType {
    Single,
    Double,
    Twin,
    Triple,
    Family,
    Suite,
    Deluxe,
    Presidential
}
