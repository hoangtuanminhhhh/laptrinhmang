package com.chat.test;

import com.chat.common.protocol.PacketFramer;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DisconnectSimulationTest {

    @Test
    void detectsDisconnectDuringFrameHeader() throws Exception {
        assertDisconnectDetected(new byte[] {0, 0});
    }

    @Test
    void detectsDisconnectDuringFramePayload() throws Exception {
        byte[] incompleteFrame = ByteBuffer.allocate(Integer.BYTES + 3)
                .putInt(8)
                .put(new byte[] {1, 2, 3})
                .array();

        assertDisconnectDetected(incompleteFrame);
    }

    private void assertDisconnectDetected(byte[] incompleteFrame) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocket listener = new ServerSocket(0, 1, loopback);
             Socket client = new Socket(loopback, listener.getLocalPort());
             Socket accepted = listener.accept()) {
            accepted.setSoTimeout(2000);
            client.getOutputStream().write(incompleteFrame);
            client.getOutputStream().flush();
            client.close();

            assertThrows(EOFException.class, () -> PacketFramer.readFrame(accepted.getInputStream()));
        }
    }
}
