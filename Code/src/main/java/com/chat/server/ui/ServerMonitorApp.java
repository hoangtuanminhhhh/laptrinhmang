package com.chat.server.ui;

import com.chat.server.core.ServerMetrics;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ServerMonitorApp extends Application {
    private static ServerMetrics metrics = new ServerMetrics();
    private int tick = 0;

    public static void attachMetrics(ServerMetrics serverMetrics) {
        if (serverMetrics != null) {
            metrics = serverMetrics;
        }
    }

    @Override
    public void start(Stage stage) {
        Label onlineLabel = new Label("Online Clients: 0");
        Label msgLabel = new Label("Messages Sent: 0");
        Label bytesLabel = new Label("Traffic: 0 B");

        HBox statsBox = new HBox(24, onlineLabel, msgLabel, bytesLabel);
        statsBox.setPadding(new Insets(10));

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time (s)");
        xAxis.setForceZeroInRange(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Online Clients");

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Server Live Monitor");
        chart.setAnimated(false);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Clients");
        chart.getData().add(series);

        Timeline loop = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            tick++;
            int online = metrics.getOnlineClients();
            long msgs = metrics.getMessagesSent();
            long bytes = metrics.getBytesTransferred();

            onlineLabel.setText("Online Clients: " + online);
            msgLabel.setText("Messages Sent: " + msgs);
            bytesLabel.setText("Traffic: " + bytes + " B");

            series.getData().add(new XYChart.Data<>(tick, online));
            if (series.getData().size() > 30) {
                series.getData().remove(0);
            }
        }));
        loop.setCycleCount(Timeline.INDEFINITE);
        loop.play();

        VBox root = new VBox(12, statsBox, chart);
        root.setPadding(new Insets(14));

        stage.setScene(new Scene(root, 720, 460));
        stage.setTitle("Server Live Monitor Dashboard");
        stage.show();
    }
}