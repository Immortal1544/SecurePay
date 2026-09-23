package com.securepay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.razorpay.RazorpayClient;
import com.securepay.entity.Order;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.Payment;
import com.securepay.entity.PaymentStatus;
import com.securepay.exception.InvalidWebhookException;
import com.securepay.repository.OrderRepository;
import com.securepay.repository.PaymentRepository;

class PaymentServiceWebhookTest {

	private static final String WEBHOOK_SECRET = "test-webhook-secret";
	private static final String RAZORPAY_ORDER_ID = "order_test_123";
	private static final String RAZORPAY_PAYMENT_ID = "pay_test_123";

	private PaymentRepository paymentRepository;
	private OrderRepository orderRepository;
	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		paymentRepository = mock(PaymentRepository.class);
		orderRepository = mock(OrderRepository.class);
		paymentService = new PaymentService(
				null,
				orderRepository,
				paymentRepository,
				(RazorpayClient) null,
				"test-key-id",
				"test-key-secret",
				WEBHOOK_SECRET);
	}

	@Test
	void validSignatureProcessesPaymentCaptured() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderId(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));

		String payload = capturedPayload();
		paymentService.processRazorpayWebhook(payload, sign(payload));

		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals(RAZORPAY_PAYMENT_ID, payment.getRazorpayPaymentId());
		assertEquals(OrderStatus.PAID, order.getStatus());
		verify(paymentRepository).save(payment);
		verify(orderRepository).save(order);
	}

	@Test
	void invalidSignatureIsRejected() {
		String payload = capturedPayload();

		assertThrows(
				InvalidWebhookException.class,
				() -> paymentService.processRazorpayWebhook(payload, "invalid-signature"));

		verifyNoInteractions(paymentRepository, orderRepository);
	}

	@Test
	void paymentFailedMarksPaymentFailedWithoutPayingOrder() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderId(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));

		String payload = failedPayload();
		paymentService.processRazorpayWebhook(payload, sign(payload));

		assertEquals(PaymentStatus.FAILED, payment.getStatus());
		assertEquals(OrderStatus.PAYMENT_PENDING, order.getStatus());
		verify(paymentRepository).save(payment);
		verify(orderRepository, never()).save(order);
	}

	@Test
	void repeatedPaymentCapturedEventIsIdempotent() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderId(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));

		String payload = capturedPayload();
		String signature = sign(payload);
		paymentService.processRazorpayWebhook(payload, signature);
		paymentService.processRazorpayWebhook(payload, signature);

		verify(paymentRepository).save(payment);
		verify(orderRepository).save(order);
		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals(OrderStatus.PAID, order.getStatus());
	}

	@Test
	void paymentFailedAfterSuccessDoesNotDowngradePayment() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderId(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));

		String capturedPayload = capturedPayload();
		paymentService.processRazorpayWebhook(capturedPayload, sign(capturedPayload));
		String failedPayload = failedPayload();
		paymentService.processRazorpayWebhook(failedPayload, sign(failedPayload));

		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals(OrderStatus.PAID, order.getStatus());
		verify(paymentRepository).save(payment);
		verify(orderRepository).save(order);
	}

	@Test
	void orderPaidEventMakesPaymentAndOrderSuccessful() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderId(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));
		String payload = """
				{"event":"order.paid","payload":{"order":{"entity":{"id":"order_test_123"}}}}
				""";

		paymentService.processRazorpayWebhook(payload, sign(payload));

		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals(OrderStatus.PAID, order.getStatus());
		verify(paymentRepository).save(payment);
		verify(orderRepository).save(order);
	}

	@Test
	void unknownRazorpayOrderIdIsIgnored() throws Exception {
		when(paymentRepository.findByRazorpayOrderId("order_unknown"))
				.thenReturn(Optional.empty());
		String payload = """
				{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_unknown","order_id":"order_unknown"}}}}
				""";

		paymentService.processRazorpayWebhook(payload, sign(payload));

		verify(paymentRepository).findByRazorpayOrderId("order_unknown");
		verify(paymentRepository, never()).save(org.mockito.ArgumentMatchers.any());
		verifyNoInteractions(orderRepository);
	}

	private Order order(OrderStatus status) {
		return new Order(null, BigDecimal.TEN, status);
	}

	private Payment payment(Order order, PaymentStatus status) {
		Payment payment = new Payment(order, BigDecimal.TEN, status);
		payment.setRazorpayOrderId(RAZORPAY_ORDER_ID);
		return payment;
	}

	private String capturedPayload() {
		return """
				{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_test_123","order_id":"order_test_123"}}}}
				""";
	}

	private String failedPayload() {
		return """
				{"event":"payment.failed","payload":{"payment":{"entity":{"id":"pay_test_123","order_id":"order_test_123"}}}}
				""";
	}

	private String sign(String payload) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return java.util.HexFormat.of()
				.formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
	}
}