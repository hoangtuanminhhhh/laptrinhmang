package com.chat.client;

import com.chat.client.net.NetworkService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/configdialog.fxml"));
            Parent root = loader.load();
            
            primaryStage.setTitle("Cấu hình kết nối Client");
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        NetworkService.getInstance().shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}