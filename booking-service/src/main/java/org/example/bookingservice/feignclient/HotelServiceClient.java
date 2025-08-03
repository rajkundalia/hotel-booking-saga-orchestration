package org.example.bookingservice.feignclient;

import org.example.common.command.CommandResult;
import org.example.common.command.ReleaseRoomCommand;
import org.example.common.command.ReserveRoomCommand;
import org.example.common.dto.ReservationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "hotel-service", url = "${services.hotel.url:http://localhost:8081}")
public interface HotelServiceClient {

    @PostMapping("/api/hotel/reserve")
    CommandResult<ReservationDto> reserveRoom(@RequestBody ReserveRoomCommand command);

    @PostMapping("/api/hotel/release")
    CommandResult<Void> releaseRoom(@RequestBody ReleaseRoomCommand command);
}