package com.chat.test;

import com.chat.client.component.DisappearingMessageTimer;
import com.chat.common.model.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử tính năng Tin nhắn tự hủy - DisappearingMessageTimer")
class DisappearingMessageTimerTest {

    private DisappearingMessageTimer timer;

    @BeforeEach
    void setUp() {
        DisappearingMessageTimer.resetInstance();
        timer = DisappearingMessageTimer.getInstance();
    }

    @AfterEach
    void tearDown() {
        if (timer != null) {
            timer.shutdown();
        }
        DisappearingMessageTimer.resetInstance();
    }

    @Test
    @DisplayName("Tự hủy tin nhắn kích hoạt đúng callback sau khi hết thời gian")
    void testMessageExpiresAndTriggersCallback() throws InterruptedException {
        String msgId = "msg-test-01";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean expired = new AtomicBoolean(false);

        // Lên lịch tự hủy sau 1 giây
        timer.schedule(msgId, 1, () -> {
            expired.set(true);
            latch.countDown();
        });

        assertTrue(timer.isScheduled(msgId), "Tin nhắn phải ở trạng thái scheduled");
        assertEquals(1, timer.getActiveCount(), "Số lượng task theo dõi phải là 1");

        boolean reachedZero = latch.await(3, TimeUnit.SECONDS);
        assertTrue(reachedZero, "Callback tự hủy phải được gọi trong vòng 3 giây");
        assertTrue(expired.get(), "Trạng thái expired phải là true");
        assertFalse(timer.isScheduled(msgId), "Sau khi tự hủy, tin nhắn không còn trong danh sách active");
        assertEquals(0, timer.getActiveCount(), "Active count phải về 0");
    }

    @Test
    @DisplayName("Đồng hồ đếm ngược (onTick) nhận đủ các bước giảm dần theo từng giây")
    void testCountdownTicks() throws InterruptedException {
        String msgId = "msg-test-ticks";
        List<Long> ticks = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        timer.scheduleWithCountdown(msgId, null, null, 2, remaining -> {
            synchronized (ticks) {
                ticks.add(remaining);
            }
        }, latch::countDown);

        boolean finished = latch.await(4, TimeUnit.SECONDS);
        assertTrue(finished, "Tác vụ phải hoàn tất");

        synchronized (ticks) {
            assertFalse(ticks.isEmpty(), "Phải nhận được ít nhất 1 lần tick");
            // Giá trị khởi điểm hoặc tick phải chứa 2 hoặc 1
            assertTrue(ticks.contains(2L) || ticks.contains(1L), "Các tick phải phản ánh đúng số giây đếm ngược");
        }
    }

    @Test
    @DisplayName("Hủy đếm ngược thành công trước khi tin nhắn tự hủy")
    void testCancelScheduledMessage() throws InterruptedException {
        String msgId = "msg-test-cancel";
        AtomicBoolean expired = new AtomicBoolean(false);

        timer.schedule(msgId, 5, () -> expired.set(true));

        assertTrue(timer.isScheduled(msgId));
        assertTrue(timer.getRemainingSeconds(msgId) > 0);

        // Người dùng hủy đếm ngược (ví dụ: thu hồi hoặc ghim tin nhắn)
        boolean cancelled = timer.cancel(msgId);
        assertTrue(cancelled, "Lệnh cancel phải trả về true");
        assertFalse(timer.isScheduled(msgId), "Tin nhắn không còn trong hàng đợi sau khi hủy");
        assertEquals(-1, timer.getRemainingSeconds(msgId), "Remaining seconds phải trả về -1");

        // Đợi 2 giây để xác nhận callback không bao giờ bị kích hoạt
        Thread.sleep(2000);
        assertFalse(expired.get(), "Callback không được phép kích hoạt sau khi đã bị hủy");
    }

    @Test
    @DisplayName("Hủy toàn bộ (cancelAll) dọn sạch hàng đợi")
    void testCancelAll() {
        timer.schedule("msg-1", 10, null);
        timer.schedule("msg-2", 10, null);
        timer.schedule("msg-3", 10, null);

        assertEquals(3, timer.getActiveCount());

        timer.cancelAll();
        assertEquals(0, timer.getActiveCount());
        assertFalse(timer.isScheduled("msg-1"));
        assertFalse(timer.isScheduled("msg-2"));
        assertFalse(timer.isScheduled("msg-3"));
    }

    @Test
    @DisplayName("Tích hợp trực tiếp với model Message của hệ thống")
    void testScheduleWithMessageModel() throws InterruptedException {
        Message message = new Message();
        message.setId(101);
        message.setMsgId("uuid-msg-101");
        message.setContent("Nội dung bí mật tự hủy sau 1s");

        CountDownLatch latch = new CountDownLatch(1);
        List<Message> expiredMessages = new ArrayList<>();

        timer.schedule(message, null, null, 1, msg -> {
            expiredMessages.add(msg);
            latch.countDown();
        });

        assertTrue(timer.isScheduled("uuid-msg-101"));

        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertTrue(done, "Callback phải được gọi");
        assertEquals(1, expiredMessages.size());
        assertEquals("uuid-msg-101", expiredMessages.get(0).getMsgId());
        assertEquals("Nội dung bí mật tự hủy sau 1s", expiredMessages.get(0).getContent());
    }

    @Test
    @DisplayName("Xử lý ngoại lệ đầu vào không hợp lệ")
    void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> timer.schedule(null, 5, null));
        assertThrows(IllegalArgumentException.class, () -> timer.schedule("", 5, null));
        assertThrows(IllegalArgumentException.class, () -> timer.schedule("msg-x", 0, null));
        assertThrows(IllegalArgumentException.class, () -> timer.schedule("msg-x", -5, null));
    }
}
