package org.example.hotelservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.common.command.CommandResult;
import org.example.common.command.ReleaseRoomCommand;
import org.example.common.command.ReserveRoomCommand;
import org.example.common.dto.ReservationDto;
import org.example.hotelservice.service.HotelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hotel")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    @PostMapping("/reserve")
    public ResponseEntity<CommandResult<ReservationDto>> reserveRoom(
            @RequestBody ReserveRoomCommand command) {
        CommandResult<ReservationDto> result = hotelService.reserveRoom(command);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/release")
    public ResponseEntity<CommandResult<Void>> releaseRoom(
            @RequestBody ReleaseRoomCommand command) {
        CommandResult<Void> result = hotelService.releaseRoom(command);
        return ResponseEntity.ok(result);
    }
}