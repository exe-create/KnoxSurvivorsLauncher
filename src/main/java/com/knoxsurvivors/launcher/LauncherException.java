package com.knoxsurvivors.launcher;

final class LauncherException extends Exception {
    LauncherException(String message) {
        super(message);
    }

    LauncherException(String message, Throwable cause) {
        super(message, cause);
    }
}
