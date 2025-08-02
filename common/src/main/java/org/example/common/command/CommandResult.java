package org.example.common.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommandResult<T> {
    private boolean success;
    private T data;
    private String errorMessage;
    private String errorCode;

    public static <T> CommandResult<T> success(T data) {
        return new CommandResult<>(true, data, null, null);
    }

    public static <T> CommandResult<T> failure(String errorMessage, String errorCode) {
        return new CommandResult<>(false, null, errorMessage, errorCode);
    }
}