package com.securepay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import com.razorpay.RazorpayClient;
import com.securepay.entity.Order;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.Payment;
import com.securepay.entity.PaymentStatus;
import com.securepay.exception.InvalidWebhookException;
import com.securepay.repository.OrderRepository;
import com.securepay.repository.PaymentRepository;

import jakarta.persistence.LockModeType;

class PaymentServiceWebhookTest {

	private static final String WEBHOOK_SECRET = "test-webhook-secret";
	private static final String RAZORPAY_ORDER_ID = "order_test_123";
	private static final String RAZORPAY_PAYMENT_ID = "pay_test_123";

	private PaymentRepository paymentRepository;
	private InventoryReservationService inventoryReservationService;
	private OrderRepository orderRepository;
	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		paymentRepository = mock(PaymentRepository.class);
		inventoryReservationService = mock(InventoryReservationService.class);
		orderRepository = mock(OrderRepository.class);
		paymentService = new PaymentService(
				null,
				orderRepository,
				paymentRepository,
				inventoryReservationService,
				(RazorpayClient) null,
				"test-key-id",
				"test-key-secret",
				WEBHOOK_SECRET);
	}

	@Test
	void validSignatureProcessesPaymentCapturedAndUsesLockedLookup() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));
		when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

		String payload = capturedPayload();
		paymentService.processRazorpayWebhook(payload, sign(payload));

		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals(RAZORPAY_PAYMENT_ID, payment.getRazorpayPaymentId());
		assertEquals(OrderStatus.PAID, order.getStatus());
		verify(paymentRepository).findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID);
		verify(paymentRepository, never()).findByRazorpayOrderId(RAZORPAY_ORDER_ID);
		verify(paymentRepository).save(payment);
		verify(orderRepository).save(order);
	}

	@Test
	void webhookLookupMethodUsesPessimisticWriteLock() throws Exception {
		Method method = PaymentRepository.class.getMethod(
				"findByRazorpayOrderIdForUpdate", String.class);

		assertNotNull(method.getAnnotation(Lock.class));
		assertEquals(LockModeType.PESSIMISTIC_WRITE, method.getAnnotation(Lock.class).value());
	}

	@Test
	void repeatedPaymentCapturedEventDoesNotSaveAgain() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));

		String payload = capturedPayload();
		String signature = sign(payload);
		paymentService.processRazorpayWebhook(payload, signature);
		paymentService.processRazorpayWebhook(payload, signature);

		verify(paymentRepository, times(1)).save(payment);
		verify(orderRepository, times(1)).save(order);
		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals(OrderStatus.PAID, order.getStatus());
	}

	@Test
	void paymentCapturedFillsMissingPaymentIdAfterOrderPaid() throws Exception {
		Order order = order(OrderStatus.PAID);
		Payment payment = payment(order, PaymentStatus.SUCCESS);
		payment.setRazorpayPaymentId(null);
		when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));

		String payload = capturedPayload();
		paymentService.processRazorpayWebhook(payload, sign(payload));

		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals(RAZORPAY_PAYMENT_ID, payment.getRazorpayPaymentId());
		assertEquals(OrderStatus.PAID, order.getStatus());
		verify(paymentRepository).save(payment);
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void paymentCapturedDoesNotOverwriteExistingPaymentIdOrRewriteSuccessfulState() throws Exception {
		Order order = order(OrderStatus.PAID);
		Payment payment = payment(order, PaymentStatus.SUCCESS);
		payment.setRazorpayPaymentId("pay_existing");
		when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));

		String payload = capturedPayload();
		paymentService.processRazorpayWebhook(payload, sign(payload));

		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals("pay_existing", payment.getRazorpayPaymentId());
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void paymentFailedMarksPaymentFailedWithoutPayingOrder() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));

		String payload = failedPayload();
		paymentService.processRazorpayWebhook(payload, sign(payload));

		assertEquals(PaymentStatus.FAILED, payment.getStatus());
		assertEquals(OrderStatus.PAYMENT_PENDING, order.getStatus());
		assertTrue(order.getInventoryReserved());
		verify(paymentRepository).save(payment);
		verify(orderRepository, never()).save(order);
		verifyNoInteractions(inventoryReservationService);
	}

	@Test
	void repeatedPaymentFailedEventDoesNotSaveAgain() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));

		String payload = failedPayload();
		String signature = sign(payload);
		paymentService.processRazorpayWebhook(payload, signature);
		paymentService.processRazorpayWebhook(payload, signature);

		assertEquals(PaymentStatus.FAILED, payment.getStatus());
		verify(paymentRepository, times(1)).save(payment);
		verify(orderRepository, never()).save(any(Order.class));
		verifyNoInteractions(inventoryReservationService);
	}

	@Test
	void lateCapturedEventDoesNotResurrectCancelledOrderOrReleasedInventory() throws Exception {
		Order order = order(OrderStatus.CANCELLED);
		order.setInventoryReserved(false);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));

		String payload = capturedPayload();
		paymentService.processRazorpayWebhook(payload, sign(payload));

		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals(OrderStatus.CANCELLED, order.getStatus());
		assertEquals(false, order.getInventoryReserved());
		verify(orderRepository, never()).save(order);
		verifyNoInteractions(inventoryReservationService);
	}

	@Test
	void paymentFailedAfterSuccessDoesNotDowngradePayment() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
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
	void firstOrderPaidEventMakesPaymentAndOrderSuccessful() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));
		String payload = orderPaidPayload();

		paymentService.processRazorpayWebhook(payload, sign(payload));

		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals(OrderStatus.PAID, order.getStatus());
		verify(paymentRepository).save(payment);
		verify(orderRepository).save(order);
	}

	@Test
	void repeatedOrderPaidEventDoesNotSaveAgain() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));
		String payload = orderPaidPayload();
		String signature = sign(payload);

		paymentService.processRazorpayWebhook(payload, signature);
		paymentService.processRazorpayWebhook(payload, signature);

		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals(OrderStatus.PAID, order.getStatus());
		verify(paymentRepository, times(1)).save(payment);
		verify(orderRepository, times(1)).save(order);
	}

	@Test
	void orderPaidAfterPaymentCapturedDoesNotRewriteSuccessfulRecords() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
				.thenReturn(Optional.of(payment));
		String captured = capturedPayload();
		String paid = orderPaidPayload();

		paymentService.processRazorpayWebhook(captured, sign(captured));
		paymentService.processRazorpayWebhook(paid, sign(paid));

		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals(OrderStatus.PAID, order.getStatus());
		assertEquals(RAZORPAY_PAYMENT_ID, payment.getRazorpayPaymentId());
		verify(paymentRepository, times(1)).save(payment);
		verify(orderRepository, times(1)).save(order);
	}

	@Test
	void orderPaidDoesNotRegressFulfillmentOrCancelledOrderStatuses() throws Exception {
		for (OrderStatus status : new OrderStatus[] {
				OrderStatus.PROCESSING,
				OrderStatus.SHIPPED,
				OrderStatus.DELIVERED,
				OrderStatus.CANCELLED }) {
			Order order = order(status);
			Payment payment = payment(order, PaymentStatus.PENDING);
			when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
					.thenReturn(Optional.of(payment));
			String payload = orderPaidPayload();

			paymentService.processRazorpayWebhook(payload, sign(payload));

			assertEquals(status, order.getStatus(), status.name());
			assertEquals(PaymentStatus.SUCCESS, payment.getStatus(), status.name());
			verify(paymentRepository).save(payment);
			verify(orderRepository, never()).save(order);
			org.mockito.Mockito.clearInvocations(paymentRepository, orderRepository);
		}
	}

	@Test
	void orderPaidAllowsCreatedAndPaymentPendingOrders() throws Exception {
		for (OrderStatus status : new OrderStatus[] { OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING }) {
			Order order = order(status);
			Payment payment = payment(order, PaymentStatus.PENDING);
			when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
					.thenReturn(Optional.of(payment));
			String payload = orderPaidPayload();

			paymentService.processRazorpayWebhook(payload, sign(payload));

			assertEquals(OrderStatus.PAID, order.getStatus(), status.name());
			assertEquals(PaymentStatus.SUCCESS, payment.getStatus(), status.name());
			verify(paymentRepository).save(payment);
			verify(orderRepository).save(order);
			org.mockito.Mockito.clearInvocations(paymentRepository, orderRepository);
		}
	}

	@Test
	void invalidSignatureIsRejectedBeforeRepositoryAccess() {
		String payload = capturedPayload();

		assertThrows(
				InvalidWebhookException.class,
				() -> paymentService.processRazorpayWebhook(payload, "invalid-signature"));

		verifyNoInteractions(paymentRepository, orderRepository);
	}

	@Test
	void missingSignatureIsRejectedBeforeRepositoryAccess() {
		assertThrows(
				InvalidWebhookException.class,
				() -> paymentService.processRazorpayWebhook(capturedPayload(), null));

		verifyNoInteractions(paymentRepository, orderRepository);
	}

	@Test
	void malformedJsonWithValidSignatureIsRejected() throws Exception {
		String payload = "{not-json";

		assertThrows(
				InvalidWebhookException.class,
				() -> paymentService.processRazorpayWebhook(payload, sign(payload)));

		verifyNoInteractions(paymentRepository, orderRepository);
	}

	@Test
	void missingOrNullEventIsRejectedWithoutRepositoryAccess() throws Exception {
		for (String payload : new String[] { "{}", "{\"event\":null}" }) {
			assertThrows(
					InvalidWebhookException.class,
					() -> paymentService.processRazorpayWebhook(payload, sign(payload)));
		}

		verifyNoInteractions(paymentRepository, orderRepository);
	}

	@Test
	void unknownValidEventIsIgnored() throws Exception {
		String payload = "{\"event\":\"subscription.activated\"}";

		paymentService.processRazorpayWebhook(payload, sign(payload));

		verifyNoInteractions(paymentRepository, orderRepository);
	}

	@Test
	void unknownRazorpayOrderIdIsIgnored() throws Exception {
		when(paymentRepository.findByRazorpayOrderIdForUpdate("order_unknown"))
				.thenReturn(Optional.empty());
		String payload = """
				{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_unknown","order_id":"order_unknown"}}}}
				""";

		paymentService.processRazorpayWebhook(payload, sign(payload));

		verify(paymentRepository).findByRazorpayOrderIdForUpdate("order_unknown");
		verify(paymentRepository, never()).save(any(Payment.class));
		verifyNoInteractions(orderRepository);
	}

	private Order order(OrderStatus status) {
		return new Order(null, BigDecimal.TEN, status);
	}

	private Payment payment(Order order, PaymentStatus status) {
		Payment payment = new Payment(order, BigDecimal.TEN, status);
		payment.setRazorpayOrderId(RAZORPAY_ORDER_ID);
		when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
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

	private String orderPaidPayload() {
		return """
				{"event":"order.paid","payload":{"order":{"entity":{"id":"order_test_123"}}}}
				""";
	}

	private String sign(String payload) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return java.util.HexFormat.of()
				.formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
	}
}
