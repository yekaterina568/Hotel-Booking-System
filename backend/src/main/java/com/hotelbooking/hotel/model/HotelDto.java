package com.hotelbooking.hotel.model;

import com.hotelbooking.hotel.db.HotelEntity;

public record HotelDto(Long hotelId, String name, String city, String address, String description, boolean defaultHotel) {
    public static HotelDto fromEntity(HotelEntity hotel) {
        return new HotelDto(hotel.getHotelId(), hotel.getName(), hotel.getCity(), hotel.getAddress(),
                hotel.getDescription(), hotel.getHotelId() == HotelEntity.DEFAULT_HOTEL_ID);
    }
}
