package com.hotelbooking.api;
import static com.hotelbooking.api.ApiDtos.*;
import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.hotel.db.HotelEntity;
import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.archive.db.BookingArchiveEntity;
import com.hotelbooking.cost.db.CostEntity;
import com.hotelbooking.payment.db.PaymentEntity;
import com.hotelbooking.refund.db.RefundEntity;
import com.hotelbooking.keycard.db.KeycardEntity;
import com.hotelbooking.review.db.ReviewEntity;
public final class Views {
    private Views() {}
    public static GuestResponse guest(GuestEntity e) { return new GuestResponse(e.getGuestId(),e.getFirstName(),e.getLastName()); }
    public static HotelResponse hotel(HotelEntity e) { return new HotelResponse(e.getHotelId(),e.getName(),e.getCity(),e.getTimezone()); }
    public static RoomResponse room(RoomEntity e) { return new RoomResponse(e.getRoomId(),e.getHotel().getHotelId(),e.getHotel().getName(),e.getRoomNumber(),e.getRoomType(),e.getCleaningState(),e.getPricePerNight()); }
    public static BookingResponse booking(BookingEntity e) { return new BookingResponse(e.getBookingId(),e.getGuest().getGuestId(),e.getGuest().getFirstName()+" "+e.getGuest().getLastName(),e.getRoom().getRoomId(),e.getRoom().getRoomNumber(),e.getRoom().getHotel().getHotelId(),e.getState().name(),e.getExpectedArrival(),e.getCheckInDate(),e.getCheckOutDate(),e.getRoom().getCleaningState(),e.getCheckedInAt()); }
    public static CostResponse cost(CostEntity e) { return new CostResponse(e.getBooking().getBookingId(),e.getNights(),e.getNightlyRate(),e.getSubtotal(),e.getServiceFee(),e.getTotal(),e.getCurrency(),e.isEstimatedLegacy()); }
    public static PaymentResponse payment(PaymentEntity e) { return new PaymentResponse(e.getPaymentId(),e.getBooking().getBookingId(),e.getAmount(),e.getCurrency(),e.getState(),e.getProviderReference(),e.getCreatedAt()); }
    public static RefundResponse refund(RefundEntity e) { return new RefundResponse(e.getRefundId(),e.getBooking().getBookingId(),e.getPayment()==null?null:e.getPayment().getPaymentId(),e.getAmount(),e.getRefundReason(),e.getState().name(),e.getProcessedAt()); }
    public static KeycardResponse keycard(KeycardEntity e) { return new KeycardResponse(e.getKeycardId(),e.getBooking().getBookingId(),Boolean.TRUE.equals(e.getIsActive()),e.getIssueTimestamp()); }
    public static ReviewResponse review(ReviewEntity e) { return new ReviewResponse(e.getReviewId(),e.getBooking().getBookingId(),e.getBooking().getRoom().getHotel().getHotelId(),e.getRating(),e.getComment()); }
    public static ArchiveResponse archive(BookingArchiveEntity e) { return new ArchiveResponse(e.getBookingId(),e.getGuest().getGuestId(),e.getRoom().getRoomId(),e.getState().name(),e.getCheckInDate(),e.getCheckOutDate(),e.getArchivedAt()); }
}
