package com.chat.test;

import javafx.application.Application;

/** Launcher wrapper for starting the JavaFX disappearing-message demo from an IDE. */
public final class DisappearingMessageDemoLauncher {
    private DisappearingMessageDemoLauncher() { }

    public static void main(String[] args) {
        Application.launch(DisappearingMessageDemoApp.class, args);
    }
}
