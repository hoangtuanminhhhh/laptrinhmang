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
        Label onlineLabel = new Label("Online: 0");
        Label msgLabel = new Label("Messages: 0 (0.0 msg/s)");
        Label trafficLabel = new Label("Traffic: 0 KB (0.0 KB/s)");
        Label cpuLabel = new Label("CPU: 0.0%");
        Label ramLabel = new Label("RAM: 0.0 / 0 MB");
        Label rejectedLabel = new Label("Rejected: 0");

        String badgeStyle = "-fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 6 10; "
                + "-fx-background-color: #f1f5f9; -fx-background-radius: 6;";
        onlineLabel.setStyle(badgeStyle);
        msgLabel.setStyle(badgeStyle);
        trafficLabel.setStyle(badgeStyle);
        cpuLabel.setStyle(badgeStyle);
        ramLabel.setStyle(badgeStyle);
        rejectedLabel.setStyle(badgeStyle);

        HBox row1 = new HBox(12, onlineLabel, msgLabel, trafficLabel);
        HBox row2 = new HBox(12, cpuLabel, ramLabel, rejectedLabel);
        VBox statsBox = new VBox(8, row1, row2);
        statsBox.setPadding(new Insets(6));

        // Biểu đồ 1: Online Clients & Tốc độ tin nhắn (msg/s)
        NumberAxis xAxis1 = new NumberAxis();
        xAxis1.setLabel("Time (s)");
        xAxis1.setForceZeroInRange(false);
        NumberAxis yAxis1 = new NumberAxis();
        yAxis1.setLabel("Count / Rate");

        LineChart<Number, Number> trafficChart = new LineChart<>(xAxis1, yAxis1);
        trafficChart.setTitle("Real-time Clients & Throughput (msg/s)");
        trafficChart.setAnimated(false);

        XYChart.Series<Number, Number> clientSeries = new XYChart.Series<>();
        clientSeries.setName("Online Clients");
        XYChart.Series<Number, Number> throughputSeries = new XYChart.Series<>();
        throughputSeries.setName("Messages / sec");
        trafficChart.getData().addAll(clientSeries, throughputSeries);

        // Biểu đồ 2: Tài nguyên CPU (%) & RAM (MB)
        NumberAxis xAxis2 = new NumberAxis();
        xAxis2.setLabel("Time (s)");
        xAxis2.setForceZeroInRange(false);
        NumberAxis yAxis2 = new NumberAxis();
        yAxis2.setLabel("CPU (%) / RAM (MB)");

        LineChart<Number, Number> resourceChart = new LineChart<>(xAxis2, yAxis2);
        resourceChart.setTitle("Server Resource Usage (CPU & RAM)");
        resourceChart.setAnimated(false);

        XYChart.Series<Number, Number> cpuSeries = new XYChart.Series<>();
        cpuSeries.setName("Process CPU (%)");
        XYChart.Series<Number, Number> ramSeries = new XYChart.Series<>();
        ramSeries.setName("Used RAM (MB)");
        resourceChart.getData().addAll(cpuSeries, ramSeries);

        HBox chartsBox = new HBox(12, trafficChart, resourceChart);

        Timeline loop = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            tick++;
            metrics.sampleThroughput();

            int online = metrics.getOnlineClients();
            long msgs = metrics.getMessagesSent();
            double msgRate = metrics.getMessagesPerSecond();
            long bytes = metrics.getBytesTransferred();
            double kbRate = metrics.getBytesPerSecond() / 1024.0;
            double cpu = metrics.getProcessCpuLoadPercent();
            double usedRam = metrics.getUsedMemoryMB();
            double maxRam = metrics.getMaxMemoryMB();
            long rejected = metrics.getRejectedConnections();

            onlineLabel.setText("Online: " + online);
            msgLabel.setText(String.format("Messages: %d (%.1f msg/s)", msgs, msgRate));
            trafficLabel.setText(String.format("Traffic: %.1f KB (%.1f KB/s)", bytes / 1024.0, kbRate));
            cpuLabel.setText(String.format("CPU: %.1f%%", cpu));
            ramLabel.setText(String.format("RAM: %.1f / %.0f MB", usedRam, maxRam));
            rejectedLabel.setText("Rejected: " + rejected);

            appendPoint(clientSeries, tick, online);
            appendPoint(throughputSeries, tick, msgRate);
            appendPoint(cpuSeries, tick, cpu);
            appendPoint(ramSeries, tick, usedRam);
        }));
        loop.setCycleCount(Timeline.INDEFINITE);
        loop.play();

        VBox root = new VBox(12, statsBox, chartsBox);
        root.setPadding(new Insets(14));

        stage.setScene(new Scene(root, 960, 520));
        stage.setTitle("Server Live Monitor Dashboard (TV1)");
        stage.show();
    }

    private void appendPoint(XYChart.Series<Number, Number> series, int x, Number y) {
        series.getData().add(new XYChart.Data<>(x, y));
        if (series.getData().size() > 30) {
            series.getData().remove(0);
        }
    }
}