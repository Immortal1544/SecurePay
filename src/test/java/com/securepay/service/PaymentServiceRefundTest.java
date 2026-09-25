package com.securepay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.razorpay.PaymentClient;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import com.securepay.dto.payment.VerifyPaymentRequest;
import com.securepay.entity.Order;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.Payment;
import com.securepay.entity.PaymentStatus;
import com.securepay.entity.Role;
import com.securepay.entity.User;
import com.securepay.exception.InvalidPaymentException;
import com.securepay.exception.InvalidPaymentStateException;
import com.securepay.exception.RazorpayPaymentException;
import com.securepay.repository.OrderRepository;
import com.securepay.repository.PaymentRepository;
import com.securepay.repository.UserRepository;

class PaymentServiceRefundTest {

	private static final Long ORDER_ID = 20L;
	private static final Long ADMIN_ID = 10L;
	private static final String ADMIN_EMAIL = "admin@example.com";
	private static final String USER_EMAIL = "customer@example.com";
	private static final String RAZORPAY_PAYMENT_ID = "pay_test_123";
	private static final String RAZORPAY_ORDER_ID = "order_test_123";
	private static final String KEY_SECRET = "test-key-secret";
	private static final String WEBHOOK_SECRET = "test-webhook-secret";

	private UserRepository userRepository;
	private OrderRepository orderRepository;
	private PaymentRepository paymentRepository;
	private PaymentClient razorpayPayments;
	private PaymentService paymentService;
	private User admin;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		orderRepository = mock(OrderRepository.class);
		paymentRepository = mock(PaymentRepository.class);
		RazorpayClient razorpayClient = mock(RazorpayClient.class);
		razorpayPayments = mock(PaymentClient.class);
		razorpayClient.payments = razorpayPayments;
		paymentService = new PaymentService(
				userRepository,
				orderRepository,
				paymentRepository,
				razorpayClient,
				"test-key-id",
				KEY_SECRET,
				WEBHOOK_SECRET);

		admin = new User("Admin", ADMIN_EMAIL, "password", Role.ADMIN);
		admin.setId(ADMIN_ID);
		setAuthenticatedUser(ADMIN_EMAIL);
		when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void nonAdminCannotInitiateRefund() {
		User user = new User("Customer", USER_EMAIL, "password", Role.USER);
		user.setId(11L);
		setAuthenticatedUser(USER_EMAIL);
		when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));

		assertThrows(AccessDeniedException.class, () -> paymentService.refundOrderPayment(ORDER_ID));
		verifyNoInteractions(paymentRepository, razorpayPayments);
	}

	@Test
	void adminRefundsSuccessfulPaymentForStoredFullAmountAndCancelsOrder() throws Exception {
		Order order = order(OrderStatus.PAID);
		Payment payment = payment(order, PaymentStatus.SUCCESS);
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));
		when(paymentRepository.save(payment)).thenReturn(payment);
		when(razorpayPayments.refund(eq(RAZORPAY_PAYMENT_ID), any(JSONObject.class)))
				.thenReturn(mock(Refund.class));

		var response = paymentService.refundOrderPayment(ORDER_ID);

		assertEquals(PaymentStatus.REFUNDED, response.getStatus());
		assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
		assertEquals(OrderStatus.CANCELLED, order.getStatus());
		ArgumentCaptor<JSONObject> refundRequest = ArgumentCaptor.forClass(JSONObject.class);
		verify(razorpayPayments).refund(eq(RAZORPAY_PAYMENT_ID), refundRequest.capture());
		assertEquals(1000, refundRequest.getValue().getInt("amount"));
		verify(paymentRepository).save(payment);
		verify(orderRepository).save(order);
	}

	@Test
	void alreadyRefundedPaymentReturnsExistingStateWithoutAnotherRazorpayRequest() {
		Order order = order(OrderStatus.CANCELLED);
		Payment payment = payment(order, PaymentStatus.REFUNDED);
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));

		var response = paymentService.refundOrderPayment(ORDER_ID);

		assertEquals(PaymentStatus.REFUNDED, response.getStatus());
		verifyNoInteractions(razorpayPayments);
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void alreadyRefundedPaymentEnsuresOrderIsCancelledWithoutAnotherRazorpayRequest() {
		Order order = order(OrderStatus.PROCESSING);
		Payment payment = payment(order, PaymentStatus.REFUNDED);
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));

		var response = paymentService.refundOrderPayment(ORDER_ID);

		assertEquals(PaymentStatus.REFUNDED, response.getStatus());
		assertEquals(OrderStatus.CANCELLED, order.getStatus());
		verifyNoInteractions(razorpayPayments);
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(orderRepository).save(order);
	}

	@Test
	void refundRejectsEveryPaymentStateExceptSuccess() {
		for (PaymentStatus status : List.of(
				PaymentStatus.CREATED, PaymentStatus.PENDING, PaymentStatus.FAILED)) {
			Payment payment = payment(order(OrderStatus.PAID), status);
			when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));

			assertThrows(InvalidPaymentStateException.class,
					() -> paymentService.refundOrderPayment(ORDER_ID), status.name());
			org.mockito.Mockito.clearInvocations(paymentRepository);
		}
		verifyNoInteractions(razorpayPayments);
	}

	@Test
	void refundRejectsSuccessfulPaymentWithoutRazorpayPaymentId() {
		Payment payment = payment(order(OrderStatus.PAID), PaymentStatus.SUCCESS);
		payment.setRazorpayPaymentId("  ");
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));

		assertThrows(InvalidPaymentStateException.class, () -> paymentService.refundOrderPayment(ORDER_ID));
		verifyNoInteractions(razorpayPayments);
	}

	@Test
	void refundRejectsOrdersOutsideCancellationLifecycle() {
		for (OrderStatus status : List.of(
				OrderStatus.CREATED,
				OrderStatus.PAYMENT_PENDING,
				OrderStatus.SHIPPED,
				OrderStatus.DELIVERED)) {
			Payment payment = payment(order(status), PaymentStatus.SUCCESS);
			when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));

			assertThrows(InvalidPaymentStateException.class,
					() -> paymentService.refundOrderPayment(ORDER_ID), status.name());
			org.mockito.Mockito.clearInvocations(paymentRepository);
		}
		verifyNoInteractions(razorpayPayments);
	}

	@Test
	void razorpayFailureDoesNotMarkPaymentOrOrderRefunded() throws Exception {
		Order order = order(OrderStatus.PAID);
		Payment payment = payment(order, PaymentStatus.SUCCESS);
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));
		when(razorpayPayments.refund(eq(RAZORPAY_PAYMENT_ID), any(JSONObject.class)))
				.thenThrow(new RazorpayException("sensitive provider detail"));

		RazorpayPaymentException error = assertThrows(
				RazorpayPaymentException.class, () -> paymentService.refundOrderPayment(ORDER_ID));

		assertEquals("Unable to communicate with Razorpay", error.getMessage());
		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals(OrderStatus.PAID, order.getStatus());
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void successfulRefundLeavesAlreadyCancelledOrderCancelled() throws Exception {
		Order order = order(OrderStatus.CANCELLED);
		Payment payment = payment(order, PaymentStatus.SUCCESS);
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));
		when(razorpayPayments.refund(eq(RAZORPAY_PAYMENT_ID), any(JSONObject.class)))
				.thenReturn(mock(Refund.class));

		paymentService.refundOrderPayment(ORDER_ID);

		assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
		assertEquals(OrderStatus.CANCELLED, order.getStatus());
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void lateVerificationAndSuccessfulWebhooksCannotRestoreRefundedPayment() throws Exception {
		Order order = order(OrderStatus.CANCELLED);
		Payment payment = payment(order, PaymentStatus.REFUNDED);
		User customer = new User("Customer", USER_EMAIL, "password", Role.USER);
		customer.setId(11L);
		setAuthenticatedUser(USER_EMAIL);
		when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(customer));
		when(orderRepository.findByIdAndUserId(ORDER_ID, 11L)).thenReturn(Optional.of(order));
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));
		VerifyPaymentRequest request = verifyRequest();

		assertEquals(PaymentStatus.REFUNDED, paymentService.verifyPayment(ORDER_ID, request).getStatus());

		for (String payload : List.of(capturedPayload(), orderPaidPayload())) {
			when(paymentRepository.findByRazorpayOrderIdForUpdate(RAZORPAY_ORDER_ID))
					.thenReturn(Optional.of(payment));
			paymentService.processRazorpayWebhook(payload, sign(payload, WEBHOOK_SECRET));
		}

		assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
		assertEquals(OrderStatus.CANCELLED, order.getStatus());
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(orderRepository, never()).save(any(Order.class));
	}

	private Order order(OrderStatus status) {
		Order order = new Order(null, BigDecimal.TEN, status);
		order.setId(ORDER_ID);
		return order;
	}

	private Payment payment(Order order, PaymentStatus status) {
		Payment payment = new Payment(order, BigDecimal.TEN, status);
		payment.setRazorpayPaymentId(RAZORPAY_PAYMENT_ID);
		payment.setRazorpayOrderId(RAZORPAY_ORDER_ID);
		return payment;
	}

	private VerifyPaymentRequest verifyRequest() throws GeneralSecurityException {
		VerifyPaymentRequest request = new VerifyPaymentRequest();
		request.setRazorpayOrderId(RAZORPAY_ORDER_ID);
		request.setRazorpayPaymentId(RAZORPAY_PAYMENT_ID);
		request.setRazorpaySignature(sign(RAZORPAY_ORDER_ID + "|" + RAZORPAY_PAYMENT_ID, KEY_SECRET));
		return request;
	}

	private String capturedPayload() {
		return "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_late\",\"order_id\":\"order_test_123\"}}}}";
	}

	private String orderPaidPayload() {
		return "{\"event\":\"order.paid\",\"payload\":{\"order\":{\"entity\":{\"id\":\"order_test_123\"}}}}";
	}

	private void setAuthenticatedUser(String email) {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(email, "", List.of()));
	}

	private String sign(String payload, String secret) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
	}
}
